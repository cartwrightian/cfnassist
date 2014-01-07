package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;
import com.amazonaws.services.ec2.model.Vpc;

public class AwsFacade implements AwsProvider {
	private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	
	public static final String ENVIRONMENT_TAG = "CFN_ASSIST_ENV";
	public static final String PROJECT_TAG = "CFN_ASSIST_PROJECT"; 
	public static final String INDEX_TAG = "CFN_ASSIST_DELTA";
	
	private static final String PARAMETER_ENV = "env";
	private static final String PARAMETER_VPC = "vpc";
	private static final String PARAM_PREFIX = "::";
	
	private AmazonCloudFormationClient cfnClient;
	private List<String> reservedParameters;
	private VpcRepository vpcRepository;
	private CfnRepository cfnRepository;

	public AwsFacade(AWSCredentialsProvider credentialsProvider, Region region) {
		cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(region);
		vpcRepository = new VpcRepository(credentialsProvider, region);
		cfnRepository = new CfnRepository(cfnClient);
		reservedParameters = new LinkedList<String>();
		reservedParameters.add(PARAMETER_ENV);
		reservedParameters.add(PARAMETER_VPC);
	}

	public List<TemplateParameter> validateTemplate(String templateBody) {
		logger.info("Validating template and discovering parameters");
		ValidateTemplateRequest validateTemplateRequest = new ValidateTemplateRequest();
		validateTemplateRequest.setTemplateBody(templateBody);

		ValidateTemplateResult result = cfnClient
				.validateTemplate(validateTemplateRequest);
		List<TemplateParameter> parameters = result.getParameters();
		logger.info(String.format("Found %s parameters", parameters.size()));
		return parameters;
	}

	public List<TemplateParameter> validateTemplate(File file) throws FileNotFoundException, IOException {
		String contents = loadFileContents(file);
		return validateTemplate(contents);
	}
	
	@Override
	public String applyTemplate(File file, ProjectAndEnv projAndEnv)
			throws FileNotFoundException, IOException,
			InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		return applyTemplate(file, projAndEnv, new HashSet<Parameter>());
	}
	
	public String applyTemplate(File file, ProjectAndEnv projAndEnv, Collection<Parameter> parameters) throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		logger.info(String.format("Applying template %s for %s", file.getAbsoluteFile(), projAndEnv));	
		Vpc vpcForEnv = findVpcForEnv(projAndEnv);
		
		String contents = loadFileContents(file);	
		String stackName = createStackName(file, projAndEnv);
		logger.info("Stackname is " + stackName);
		
		String vpcId = vpcForEnv.getVpcId();
		checkParameters(parameters);
		addBuiltInParameters(projAndEnv.getEnv(), parameters, vpcId);
		EnvironmentTag envTag = new EnvironmentTag(projAndEnv.getEnv());
		addAutoDiscoveryParameters(envTag, file, parameters);
		
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		Collection<Tag> tags = createTags(projAndEnv.getProject(), projAndEnv.getEnv());
		createStackRequest.setTags(tags);
		
		logger.info("Making createStack call to AWS");
		cfnClient.createStack(createStackRequest);
		waitForCreateFinished(stackName);
		cfnRepository.updateRepositoryFor(stackName);
		return stackName;
	}

	private Collection<Tag> createTags(String project, String env) {
		Collection<Tag> tags = new ArrayList<Tag>();
		tags.add(createTag(PROJECT_TAG, project));
		tags.add(createTag(ENVIRONMENT_TAG, env));
		return tags;
	}

	private Tag createTag(String key, String value) {
		Tag tag = new Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

	private void addBuiltInParameters(String env, Collection<Parameter> parameters, String vpcId) {
		addParameterTo(parameters, PARAMETER_ENV, env);
		addParameterTo(parameters, PARAMETER_VPC, vpcId);
	}

	private Vpc findVpcForEnv(ProjectAndEnv projAndEnv) throws InvalidParameterException {
		Vpc vpcForEnv = vpcRepository.getCopyOfVpc(projAndEnv);
		if (vpcForEnv==null) {
			logger.error("Unable to find VPC tagged as environment:" + projAndEnv);
			throw new InvalidParameterException(projAndEnv.toString());
		}
		logger.info(String.format("Found VPC %s corresponding to %s", vpcForEnv.getVpcId(), projAndEnv));
		return vpcForEnv;
	}

	private void addAutoDiscoveryParameters(EnvironmentTag envTag, File file, 
			Collection<Parameter> parameters) throws FileNotFoundException,
			IOException, InvalidParameterException {
		List<Parameter> autoPopulatedParametes = this.fetchAutopopulateParametersFor(file, envTag);
		for (Parameter autoPop : autoPopulatedParametes) {
			parameters.add(autoPop);
		}
	}

	private void addParameterTo(Collection<Parameter> parameters, String parameterName, String parameterValue) {
		logger.info(String.format("Setting %s parameter to %s", parameterName, parameterValue));
		Parameter parameter = new Parameter();
		parameter.setParameterKey(parameterName);
		parameter.setParameterValue(parameterValue);
		parameters.add(parameter);
	}

	private void checkParameters(Collection<Parameter> parameters) throws InvalidParameterException {
		for(Parameter param : parameters) {
			String parameterKey = param.getParameterKey();
			if (reservedParameters.contains(parameterKey)) {
				logger.error("Attempt to overide autoset parameter called " + parameterKey);
				throw new InvalidParameterException(parameterKey);
			}
		}	
	}

	public String createStackName(File file, ProjectAndEnv projAndEnv) {
		// note: aws only allows [a-zA-Z][-a-zA-Z0-9]* in stacknames
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		return projAndEnv.getProject()+projAndEnv.getEnv()+name;
	}
	
	public String waitForCreateFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException {
		String result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS);
		if (!result.equals(StackStatus.CREATE_COMPLETE.toString())) {
			logger.error(String.format("Failed to create stack %s, status is %s", stackName, result));
			logStackEvents(cfnRepository.getStackEvents(stackName));
		}
		return result;
	}
	
	private void logStackEvents(List<StackEvent> stackEvents) {
		for(StackEvent event : stackEvents) {
			logger.info(event.toString());
		}	
	}

	public String waitForDeleteFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException {
		StackStatus requiredStatus = StackStatus.DELETE_IN_PROGRESS;
		String result = StackStatus.DELETE_FAILED.toString();
		try {
			result = cfnRepository.waitForStatusToChangeFrom(stackName, requiredStatus);
		}
		catch(com.amazonaws.AmazonServiceException awsException) {
			String errorCode = awsException.getErrorCode();
			if (errorCode.equals("ValidationError")) {
				result = StackStatus.DELETE_COMPLETE.toString();
			} else {
				result = StackStatus.DELETE_FAILED.toString();
			}		
		}	
		
		if (!result.equals(StackStatus.DELETE_COMPLETE.toString())) {
			logger.error("Failed to delete stack, status is " + result);
			logStackEvents(cfnRepository.getStackEvents(stackName));
		}
		return result;
	}
	
	public void deleteStack(String stackName) {
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		logger.info("Requesting deletion of stack " + stackName);
		cfnClient.deleteStack(deleteStackRequest);	
	}

	private String loadFileContents(File file) throws IOException {
		return FileUtils.readFileToString(file, Charset.defaultCharset());
	}

	@Override
	public List<Parameter> fetchAutopopulateParametersFor(File file, EnvironmentTag envTag) throws FileNotFoundException, IOException, InvalidParameterException {
		logger.info(String.format("Discover and populate parameters for %s and VPC: %s", file.getAbsolutePath(), envTag));
		List<TemplateParameter> allParameters = validateTemplate(file);
		List<Parameter> matches = new LinkedList<Parameter>();
		for(TemplateParameter templateParam : allParameters) {
			String name = templateParam.getParameterKey();
			if (isBuiltInParamater(name))
			{
				continue;
			}
			logger.info("Checking if parameter should be auto-populated from an existing resource, param name is " + name);
			String description = templateParam.getDescription();
			if (shouldPopulateFor(description)) {
				populateParameter(envTag, matches, name, description);
			}
		}
		return matches;
	}

	private boolean isBuiltInParamater(String name) {
		boolean result = name.equals(PARAMETER_ENV);
		if (result) {
			logger.info("Found built in parameter");
		}
		return result;
	}

	private void populateParameter(EnvironmentTag envTag, List<Parameter> matches, String parameterName, String parameterDescription)
			throws InvalidParameterException {
		String logicalId = parameterDescription.substring(PARAM_PREFIX.length());
		logger.info("Attempt to find physical ID for LogicalID: " + logicalId);
		String value = cfnRepository.findPhysicalIdByLogicalId(envTag, logicalId);
		if (value==null) {
			String msg = String.format("Failed to find physicalID to match logicalID: %s required for parameter: %s" , logicalId, parameterName);
			logger.error(msg);
			throw new InvalidParameterException(msg);
		}
		logger.info(String.format("Found physicalID: %s matching logicalID: %s Populating this into parameter %s", value, logicalId, parameterName));
		addParameterTo(matches, parameterName, value);
	}

	private boolean shouldPopulateFor(String description) {
		if (description==null) {
			return false;
		}
		return description.startsWith(PARAM_PREFIX);
	}

	@Override
	public ArrayList<String> applyTemplatesFromFolder(String folderPath,
			ProjectAndEnv projAndEnv) throws InvalidParameterException, FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException {
		ArrayList<String> createdStacks = new ArrayList<>();
		File folder = validFolder(folderPath);
		logger.info("Invoking templates from folder: " + folderPath);
		List<File> files = loadFiles(folder);
		
		logger.info("Attempt to Validate all files");
		for(File file : files) {
			validateTemplate(file);
		}
		
		int highestAppliedDelta = getDeltaIndex(projAndEnv);
		logger.info("Current index is " + highestAppliedDelta);
		
		logger.info("Validation ok, apply template files");
		for(File file : files) {
			int deltaIndex = extractIndexFrom(file);
			if (deltaIndex>highestAppliedDelta) {
				logger.info(String.format("Apply template file: %s, index is %s",file.getAbsolutePath(), deltaIndex));
				String stackName = applyTemplate(file, projAndEnv);
				logger.info("Create stack " + stackName);
				createdStacks.add(stackName); 
				setDeltaIndex(projAndEnv, deltaIndex);
			} else {
				logger.info(String.format("Skipping file %s as already applied, index was %s", file.getAbsolutePath(), deltaIndex));
			}		
		}
		
		logger.info("All templates successfully invoked");
		
		return createdStacks;
	}

	private List<File> loadFiles(File folder) {
		FilenameFilter jsonFilter = new JsonExtensionFilter();
		File[] files = folder.listFiles(jsonFilter);
		Arrays.sort(files); // place in lexigraphical order
		return Arrays.asList(files);
	}

	private File validFolder(String folderPath)
			throws InvalidParameterException {
		File folder = new File(folderPath);
		if (!folder.isDirectory()) {
			throw new InvalidParameterException(folderPath + " is not a directory");
		}
		return folder;
	}
	
	public List<String> rollbackTemplatesInFolder(String folderPath, ProjectAndEnv projAndEnv) throws InvalidParameterException {
		List<String> stackNames = new LinkedList<String>();
		File folder = validFolder(folderPath);
		List<File> files = loadFiles(folder);
		Collections.reverse(files); // delete in reverse direction
		
		int highestAppliedDelta = getDeltaIndex(projAndEnv);
		logger.info("Current delta is " + highestAppliedDelta);
		for(File file : files) {
			int deltaIndex = extractIndexFrom(file);
			String stackName = createStackName(file, projAndEnv);
			if (deltaIndex>highestAppliedDelta) {
				logger.warn(String.format("Not deleting %s as index %s is greater than current delta %s", stackName, deltaIndex, highestAppliedDelta));
			} else {
				int newDelta = deltaIndex-1;
				logger.info(String.format("About to delete stackname %s, new delta will be %s", stackName, newDelta));
				deleteStack(stackName);
				logger.info("Deleted stack " + stackName);
				stackNames.add(stackName);
				if (newDelta>=0) {
					logger.info("Resetting delta to " + newDelta);
					this.setDeltaIndex(projAndEnv, newDelta);
				}
			}
		}
		return stackNames;
	}

	private int extractIndexFrom(File file) {
		StringBuilder indexPart = new StringBuilder();
		String name = file.getName();
		
		int i = 0;
		while(Character.isDigit(name.charAt(i)))  {
			indexPart.append(name.charAt(i));
			i++;
		}
		
		return Integer.parseInt(indexPart.toString());
	}

	@Override
	public void resetDeltaIndex(ProjectAndEnv projAndEnv) {
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
	}

	@Override
	public void setDeltaIndex(ProjectAndEnv projAndEnv, Integer index) {
		vpcRepository.setVpcIndexTag(projAndEnv, index.toString());
	}

	@Override
	public int getDeltaIndex(ProjectAndEnv projAndEnv) {
		String tag = vpcRepository.getVpcIndexTag(projAndEnv);
		return Integer.parseInt(tag);
	}



}

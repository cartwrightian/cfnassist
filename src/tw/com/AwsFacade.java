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

import tw.com.exceptions.BadVPCDeltaIndexException;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;
import com.amazonaws.services.ec2.model.Vpc;

public class AwsFacade implements AwsProvider {
	private static final String DELTA_EXTENSTION = ".delta";

	private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	
	public static final String ENVIRONMENT_TAG = "CFN_ASSIST_ENV";
	public static final String PROJECT_TAG = "CFN_ASSIST_PROJECT"; 
	public static final String INDEX_TAG = "CFN_ASSIST_DELTA";
	public static final String BUILD_TAG = "CFN_ASSIST_BUILD_NUMBER";
	public static final Object TYPE_TAG = "CFN_ASSIST_TYPE";
	public static final String COMMENT_TAG = "CFN_COMMENT";
	
	private static final String PARAMETER_ENV = "env";
	private static final String PARAMETER_VPC = "vpc";
	private static final String PARAMETER_BUILD_NUMBER = "build";
	private static final String PARAM_PREFIX = "::";

	private static final String PARAMETER_STACKNAME = "stackname";
	
	private AmazonCloudFormationClient cfnClient;
	
	private List<String> reservedParameters;
	private VpcRepository vpcRepository;
	private CfnRepository cfnRepository;
	private MonitorStackEvents monitor;

	private String commentTag="";

	public AwsFacade(MonitorStackEvents monitor, AmazonCloudFormationClient cfnClient, CfnRepository cfnRepository, VpcRepository vpcRepository) {
		this.monitor = monitor;
		this.cfnClient = cfnClient;
		this.cfnRepository = cfnRepository;
		this.vpcRepository = vpcRepository;

		reservedParameters = new LinkedList<String>();
		reservedParameters.add(PARAMETER_ENV);
		reservedParameters.add(PARAMETER_VPC);
		reservedParameters.add(PARAMETER_BUILD_NUMBER);
	}

	public List<TemplateParameter> validateTemplate(String templateBody) {
		ValidateTemplateRequest validateTemplateRequest = new ValidateTemplateRequest();
		validateTemplateRequest.setTemplateBody(templateBody);

		ValidateTemplateResult result = cfnClient
				.validateTemplate(validateTemplateRequest);
		List<TemplateParameter> parameters = result.getParameters();
		logger.info(String.format("Found %s parameters", parameters.size()));
		return parameters;
	}

	public List<TemplateParameter> validateTemplate(File file) throws FileNotFoundException, IOException {
		logger.info("Validating template and discovering parameters for file " + file.getAbsolutePath());
		String contents = loadFileContents(file);
		return validateTemplate(contents);
	}
	

	@Override
	public StackId applyTemplate(String filename, ProjectAndEnv projAndEnv) throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, IOException, InvalidParameterException, InterruptedException {
		File file = new File(filename);
		return applyTemplate(file, projAndEnv);
	}
	
	@Override
	public StackId applyTemplate(File file, ProjectAndEnv projAndEnv)
			throws FileNotFoundException, IOException,
			InvalidParameterException, WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus, DuplicateStackException {
		return applyTemplate(file, projAndEnv, new HashSet<Parameter>());
	}
	
	public StackId applyTemplate(File file, ProjectAndEnv projAndEnv, Collection<Parameter> userParameters) throws FileNotFoundException, IOException, InvalidParameterException, InterruptedException, NotReadyException, WrongNumberOfStacksException, WrongStackStatus, DuplicateStackException {
		logger.info(String.format("Applying template %s for %s", file.getAbsoluteFile(), projAndEnv));	
		Vpc vpcForEnv = findVpcForEnv(projAndEnv);
		List<TemplateParameter> declaredParameters = validateTemplate(file);

		String contents = loadFileContents(file);
		
		if (isDelta(file)) {
			return updateStack(file, projAndEnv, userParameters, vpcForEnv,
					declaredParameters, contents);
		} else {
			return createStack(file, projAndEnv, userParameters, vpcForEnv,
					declaredParameters, contents);
		}	
	}

	private boolean isDelta(File file) {
		return (file.getName().contains(DELTA_EXTENSTION));
	}

	private StackId updateStack(File file, ProjectAndEnv projAndEnv,
			Collection<Parameter> userParameters, Vpc vpcForEnv,
			List<TemplateParameter> declaredParameters, String contents) throws FileNotFoundException, InvalidParameterException, IOException, InterruptedException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus {
		logger.info("Request to update a stack, filename is " + file.getAbsolutePath());
		
		String vpcId = vpcForEnv.getVpcId();
		Collection<Parameter> parameters = createRequiredParameters(file, projAndEnv, userParameters, declaredParameters, vpcId );
		String stackName = findStackToUpdate(declaredParameters, projAndEnv);
		
		if (monitor instanceof SNSMonitor) {
			logger.debug("SNS monitoring enabled, check if stack has a notification ARN set");
			Stack target = cfnRepository.getStack(stackName);
			List<String> notificationARNs = target.getNotificationARNs();
			if (notificationARNs.size()<=0) {
				logger.error("Stack does not have notification ARN set, progress cannot be monitored via SNS");
				throw new InvalidParameterException("Cannot use SNS, original stack was not created with a notification ARN");
			}
			for(String arn : notificationARNs) {
				logger.info("Notification ARNs set on stack " + arn);
			}
		}
		
		logger.info("Will attempt to update stack: " + stackName);
		UpdateStackRequest updateStackRequest = new UpdateStackRequest();	
		updateStackRequest.setParameters(parameters);
		updateStackRequest.setStackName(stackName);
		updateStackRequest.setTemplateBody(contents);
		UpdateStackResult result = cfnClient.updateStack(updateStackRequest);

		StackId id = new StackId(stackName,result.getStackId());
		try {
			monitor.waitForUpdateFinished(id);
		} catch (WrongStackStatus stackFailedToCreate) {
			logger.error("Failed to create stack",stackFailedToCreate);
			cfnRepository.updateRepositoryFor(id);
			throw stackFailedToCreate;
		}
		cfnRepository.updateRepositoryFor(id);
		return id;
			
	}

	private String findStackToUpdate(List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv) throws InvalidParameterException {
		for(TemplateParameter param : declaredParameters) {
			if (param.getParameterKey().equals(PARAMETER_STACKNAME)) {
				String defaultValue = param.getDefaultValue();
				if ((defaultValue!=null) && (!defaultValue.isEmpty())) {
					return createName(projAndEnv, defaultValue);
				} else {
					logger.error(String.format("Found parameter %s but no default given",PARAMETER_STACKNAME));
					throw new InvalidParameterException(PARAMETER_STACKNAME); 
				}
			}
		}
		logger.error(String.format("Unable to find parameter %s which is required to peform a stack update", PARAMETER_STACKNAME));
		throw new InvalidParameterException(PARAMETER_STACKNAME);
	}

	
	private StackId createStack(File file, ProjectAndEnv projAndEnv,
			Collection<Parameter> userParameters, Vpc vpcForEnv,
			List<TemplateParameter> declaredParameters, String contents)
			throws WrongNumberOfStacksException, NotReadyException,
			WrongStackStatus, InterruptedException, DuplicateStackException,
			InvalidParameterException, FileNotFoundException, IOException {
		String stackName = createStackName(file, projAndEnv);
		logger.info("Stackname is " + stackName);
		
		handlePossibleRollback(stackName);
		
		String vpcId = vpcForEnv.getVpcId();
		
		Collection<Parameter> parameters = createRequiredParameters(file, projAndEnv, userParameters, declaredParameters, vpcId);
		
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		if (monitor instanceof SNSMonitor) {
			SNSMonitor snsMonitor = (SNSMonitor) monitor;
			createSNSMonitoring(createStackRequest, snsMonitor);
		}
		Collection<Tag> tags = createTagsForStack(projAndEnv);
		createStackRequest.setTags(tags);
		
		logger.info("Making createStack call to AWS");
		
		CreateStackResult result = cfnClient.createStack(createStackRequest);
		StackId id = new StackId(stackName,result.getStackId());
		try {
			monitor.waitForCreateFinished(id);
		} catch (WrongStackStatus stackFailedToCreate) {
			logger.error("Failed to create stack",stackFailedToCreate);
			cfnRepository.updateRepositoryFor(id);
			throw stackFailedToCreate;
		}
		cfnRepository.updateRepositoryFor(id);
		return id;
	}

	private Collection<Parameter> createRequiredParameters(File file,
			ProjectAndEnv projAndEnv, Collection<Parameter> userParameters,
			List<TemplateParameter> declaredParameters, String vpcId)
			throws InvalidParameterException, FileNotFoundException,
			IOException {
		Collection<Parameter> parameters  = new LinkedList<Parameter>();
		parameters.addAll(userParameters);
		
		checkNoClashWithBuiltInParameters(parameters);
		addBuiltInParameters(parameters, declaredParameters, projAndEnv, vpcId);
		EnvironmentTag envTag = new EnvironmentTag(projAndEnv.getEnv());
		addAutoDiscoveryParameters(envTag, file, parameters, declaredParameters);
		
		logAllParameters(parameters);
		return parameters;
	}

	private void createSNSMonitoring(CreateStackRequest createStackRequest,
			SNSMonitor snsMonitor) throws NotReadyException {
		String arn = snsMonitor.getArn();
		logger.info("Setting arn for sns events to " + arn);
		Collection<String> arns = new LinkedList<String>();
		arns.add(arn);
		createStackRequest.setNotificationARNs(arns);
	}

	private void handlePossibleRollback(String stackName)
			throws WrongNumberOfStacksException, NotReadyException,
			WrongStackStatus, InterruptedException, DuplicateStackException {
		String currentStatus = cfnRepository.getStackStatus(stackName);
		if (currentStatus.length()!=0) {
			logger.warn("Stack already exists: " + stackName);
			StackId stackId = cfnRepository.getStackId(stackName);
			if (isRollingBack(stackId)) {
				logger.warn("Stack is rolled back so delete it and recreate " + stackId);
				deleteStackNonBlocking(stackId.getStackName());
				//deleteStack(stackId);
			} else {
				logger.error("Stack exists and is not rolled back, cannot create another stack with name:" +stackName);
				throw new DuplicateStackException(stackName);
			}
		}
	}

	private boolean isRollingBack(StackId id) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		String currentStatus = cfnRepository.getStackStatus(id.getStackName());
		if (currentStatus.equals(StackStatus.ROLLBACK_IN_PROGRESS)) {
			monitor.waitForRollbackComplete(id);
			return true;
		} else if (currentStatus.equals(StackStatus.ROLLBACK_COMPLETE.toString())) {
			return true;
		}
		return false;
	}

	private void logAllParameters(Collection<Parameter> parameters) {
		logger.info("Invoking with following parameters");
		for(Parameter param : parameters) {
			logger.info(String.format("Parameter key='%s' value='%s'", param.getParameterKey(), param.getParameterValue()));
		}
	}

	private Collection<Tag> createTagsForStack(ProjectAndEnv projectAndEnv) {	
		Collection<Tag> tags = new ArrayList<Tag>();
		tags.add(createTag(PROJECT_TAG, projectAndEnv.getProject()));
		tags.add(createTag(ENVIRONMENT_TAG, projectAndEnv.getEnv()));
		if (projectAndEnv.hasBuildNumber()) {
			tags.add(createTag(BUILD_TAG, projectAndEnv.getBuildNumber()));
		}
		if (!commentTag.isEmpty()) {
			logger.info(String.format("Adding %s: %s", COMMENT_TAG, commentTag));
			tags.add(createTag(COMMENT_TAG, commentTag));
		}
		return tags;
	}
	

	@Override
	public void setCommentTag(String commentTag) {
		this.commentTag = commentTag;		
	}

	private Tag createTag(String key, String value) {
		Tag tag = new Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

	private void addBuiltInParameters(Collection<Parameter> parameters, List<TemplateParameter> declared, ProjectAndEnv projAndEnv, String vpcId) {
		addParameterTo(parameters, declared, PARAMETER_ENV, projAndEnv.getEnv());
		addParameterTo(parameters, declared, PARAMETER_VPC, vpcId);
		if (projAndEnv.hasBuildNumber()) {
			addParameterTo(parameters, declared, PARAMETER_BUILD_NUMBER, projAndEnv.getBuildNumber());
		}
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
			Collection<Parameter> parameters, List<TemplateParameter> declaredParameters) throws FileNotFoundException,
			IOException, InvalidParameterException {
		List<Parameter> autoPopulatedParametes = this.fetchAutopopulateParametersFor(file, envTag, declaredParameters);
		for (Parameter autoPop : autoPopulatedParametes) {
			parameters.add(autoPop);
		}
	}

	private void addParameterTo(Collection<Parameter> parameters, List<TemplateParameter> declared, String parameterName, String parameterValue) {
		boolean isDeclared = false;
		for(TemplateParameter declaration : declared) {
			isDeclared = (declaration.getParameterKey().equals(parameterName));
			if (isDeclared==true) break;
		}
		if (!isDeclared) {
			logger.info(String.format("Not populating parameter %s as it is not declared in the json file", parameterName));
		} else {
			logger.info(String.format("Setting %s parameter to %s", parameterName, parameterValue));
			Parameter parameter = new Parameter();
			parameter.setParameterKey(parameterName);
			parameter.setParameterValue(parameterValue);
			parameters.add(parameter);
		}
	}

	private void checkNoClashWithBuiltInParameters(Collection<Parameter> parameters) throws InvalidParameterException {
		for(Parameter param : parameters) {
			String parameterKey = param.getParameterKey();
			if (reservedParameters.contains(parameterKey)) {
				logger.error("Attempt to overide built in and autoset parameter called " + parameterKey);
				throw new InvalidParameterException(parameterKey);
			}
		}	
	}

	public String createStackName(File file, ProjectAndEnv projAndEnv) {
		// note: aws only allows [a-zA-Z][-a-zA-Z0-9]* in stacknames
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		if (name.endsWith(DELTA_EXTENSTION)) {
			logger.debug("Detected delta filename, remove additional extension");
			name = FilenameUtils.removeExtension(name);
		}
		return createName(projAndEnv, name);
	}

	private String createName(ProjectAndEnv projAndEnv, String name) {
		String project = projAndEnv.getProject();
		String env = projAndEnv.getEnv();
		if (projAndEnv.hasBuildNumber()) {
			return project+projAndEnv.getBuildNumber()+env+name;
		} else {
			return project+env+name;
		}
	}
	
	public void deleteStackFrom(File templateFile, ProjectAndEnv projectAndEnv) {
		String stackName = createStackName(templateFile, projectAndEnv);
		StackId stackId;
		try {
			stackId = cfnRepository.getStackId(stackName);
		} catch (WrongNumberOfStacksException e) {
			logger.warn("Unable to find stack " + stackName);
			return;
		}
				
		logger.info("Found ID for stack: " + stackId);
		deleteStackNonBlocking(stackName);
		
		try {
			monitor.waitForDeleteFinished(stackId);
		} catch (WrongNumberOfStacksException | NotReadyException
				| WrongStackStatus | InterruptedException e) {
			logger.error("Unable to delete stack " + stackName);
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
		}
		
	}
	
	private void deleteStackNonBlocking(String stackName) {
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		logger.info("Requesting deletion of stack " + stackName);
		cfnClient.deleteStack(deleteStackRequest);	
	}

	private String loadFileContents(File file) throws IOException {
		return FileUtils.readFileToString(file, Charset.defaultCharset());
	}

	@Override
	public List<Parameter> fetchAutopopulateParametersFor(File file, EnvironmentTag envTag, List<TemplateParameter> declaredParameters) throws FileNotFoundException, IOException, InvalidParameterException {
		logger.info(String.format("Discover and populate parameters for %s and VPC: %s", file.getAbsolutePath(), envTag));
		List<Parameter> matches = new LinkedList<Parameter>();
		for(TemplateParameter templateParam : declaredParameters) {
			String name = templateParam.getParameterKey();
			if (isBuiltInParamater(name))
			{
				continue;
			}
			logger.info("Checking if parameter should be auto-populated from an existing resource, param name is " + name);
			String description = templateParam.getDescription();
			if (shouldPopulateFor(description)) {
				populateParameter(envTag, matches, name, description, declaredParameters);
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

	private void populateParameter(EnvironmentTag envTag, List<Parameter> matches, String parameterName, String parameterDescription, List<TemplateParameter> declaredParameters)
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
		addParameterTo(matches, declaredParameters, parameterName, value);
	}

	private boolean shouldPopulateFor(String description) {
		if (description==null) {
			return false;
		}
		return description.startsWith(PARAM_PREFIX);
	}
	
	@Override
	public ArrayList<StackId> applyTemplatesFromFolder(String folderPath,
			ProjectAndEnv projAndEnv) throws InvalidParameterException,
			FileNotFoundException, IOException, CfnAssistException,
			InterruptedException {
		return applyTemplatesFromFolder(folderPath, projAndEnv, new LinkedList<Parameter>());
	}

	@Override
	public ArrayList<StackId> applyTemplatesFromFolder(String folderPath,
			ProjectAndEnv projAndEnv, Collection<Parameter> cfnParams) throws InvalidParameterException, FileNotFoundException, IOException, InterruptedException, CfnAssistException {
		ArrayList<StackId> updatedStacks = new ArrayList<>();
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
				StackId stackId = applyTemplate(file, projAndEnv, cfnParams);
				logger.info("Create/Updated stack " + stackId);
				updatedStacks.add(stackId); 
				setDeltaIndex(projAndEnv, deltaIndex);
			} else {
				logger.info(String.format("Skipping file %s as already applied, index was %s", file.getAbsolutePath(), deltaIndex));
			}		
		}
		
		logger.info("All templates successfully invoked");
		
		return updatedStacks;
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
	
	public List<String> rollbackTemplatesInFolder(String folderPath, ProjectAndEnv projAndEnv) throws InvalidParameterException, CfnAssistException {
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
				deleteStackNonBlocking(stackName);
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
	public void resetDeltaIndex(ProjectAndEnv projAndEnv) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
	}

	@Override
	public void setDeltaIndex(ProjectAndEnv projAndEnv, Integer index) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, index.toString());
	}

	@Override
	public int getDeltaIndex(ProjectAndEnv projAndEnv) throws CfnAssistException {
		String tag = vpcRepository.getVpcIndexTag(projAndEnv);
		int result = -1;
		try {
			result = Integer.parseInt(tag);
			return result;
		}
		catch(NumberFormatException exception) {
			logger.error("Could not parse the delta index: " + tag);
			throw new BadVPCDeltaIndexException(tag);
		}
	}

	public void initEnvAndProjectForVPC(String targetVpcId, ProjectAndEnv projectAndEnvToSet) throws CfnAssistException {
		Vpc result = vpcRepository.getCopyOfVpc(projectAndEnvToSet);
		if (result!=null) {
			logger.error(String.format("Managed to find vpc already present with tags %s and id %s", projectAndEnvToSet, result.getVpcId()));
			throw new TagsAlreadyInit(targetVpcId);
		}	
		vpcRepository.initAllTags(targetVpcId, projectAndEnvToSet);	
	}

//	@Override
//	@Deprecated
//	public void initEnvAndProjectForStack(String stackName,
//			ProjectAndEnv projAndEnv) throws CfnAssistException {
//		// TODO this does not work, the EC2 part of the API does not recognize the resource ID returned by the CFN part of the API
//		// This functionality is disabled at the CLI level at the moment, and the test is set ignored
//		DescribeStacksRequest request = new DescribeStacksRequest();
//		request.setStackName(stackName);
//		DescribeStacksResult result = cfnClient.describeStacks(request );
//		
//		List<Stack> stacks = result.getStacks();
//		if (stacks.size()!=1) {
//			throw new WrongNumberOfStacksException(1, stacks.size());
//		}
//		
//		Stack stack = stacks.get(0);
//		// TODO check tags not already set
//		
//		logger.info(String.format("Add %s to stackname '%s'", projAndEnv, stackName));
//		// tags here are different type from the tags during the stack create call
//		Collection<com.amazonaws.services.ec2.model.Tag> tags = new LinkedList<com.amazonaws.services.ec2.model.Tag>();
//		tags.add(new com.amazonaws.services.ec2.model.Tag().withKey(ENVIRONMENT_TAG).withValue(projAndEnv.getEnv()));
//		tags.add(new com.amazonaws.services.ec2.model.Tag().withKey(PROJECT_TAG).withValue(projAndEnv.getProject()));
//		String ids[] = { stack.getStackId() };
//		
//		CreateTagsRequest createTagsRequest= new CreateTagsRequest();
//		Collection<String> resources = Arrays.asList(ids);
//		createTagsRequest.setResources(resources);
//		createTagsRequest.setTags(tags);
//		logger.debug("Making call to createTags");
//		ec2Client.createTags(createTagsRequest);
//		
//		// TODO how to check status of this call?
//	}


}

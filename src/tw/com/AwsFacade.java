package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
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
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;
import com.amazonaws.services.ec2.model.Vpc;

public class AwsFacade implements AwsProvider {
	private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	
	// TODO things we need to ID
	// id subnet by cidr & vpc
	// id sg by TAG and VPC
	private static final String PARAMETER_ENV = "env";
	private static final String PARAMETER_VPC = "vpc";
	private static final String PARAM_PREFIX = "::";
	
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 200;
	private AmazonCloudFormationClient cfnClient;
	private List<String> reservedParameters;
	private VpcRepository vpcRepository;

	public AwsFacade(AWSCredentialsProvider credentialsProvider, Region region) {
		cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(region);
		vpcRepository = new VpcRepository(credentialsProvider, region);
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
	public String applyTemplate(File file, String env)
			throws FileNotFoundException, IOException,
			InvalidParameterException {
		return applyTemplate(file, env, new HashSet<Parameter>());
	}
	
	public String applyTemplate(File file, String env, Collection<Parameter> parameters) throws FileNotFoundException, IOException, InvalidParameterException {
		logger.info(String.format("Applying template %s for env %s", file.getAbsoluteFile(), env));	
		Vpc vpcForEnv = findVpcForEnv(env);
		
		String contents = loadFileContents(file);	
		String stackName = createStackName(file, env);
		logger.info("Stackname is " + stackName);
		
		checkParameters(parameters);
		addParameterTo(parameters, PARAMETER_ENV, env);
		addParameterTo(parameters, PARAMETER_VPC, vpcForEnv.getVpcId());
		addAutoPopulatedParameters(file, env, parameters);
		
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		
		logger.info("Making createStack call to AWS");
		cfnClient.createStack(createStackRequest);	
		return stackName;
	}

	private Vpc findVpcForEnv(String env) throws InvalidParameterException {
		Vpc vpcForEnv = vpcRepository.findVpcForEnv(env);
		if (vpcForEnv==null) {
			logger.error("Unable to find VPC tagged as environment:" + env);
			throw new InvalidParameterException(env);
		}
		logger.info(String.format("Found VPC %s corresponding to environment %s", vpcForEnv.getVpcId(), env));
		return vpcForEnv;
	}

	private void addAutoPopulatedParameters(File file, String env,
			Collection<Parameter> parameters) throws FileNotFoundException,
			IOException, InvalidParameterException {
		List<Parameter> autoPopulatedParametes = this.fetchParametersFor(file, env);
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

	public String createStackName(File file, String env) {
		// note: aws only allows [a-zA-Z][-a-zA-Z0-9]* in stacknames
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		return env+name;
	}
	
	public String waitForCreateFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException {
		StackStatus inProgressStatus = StackStatus.CREATE_IN_PROGRESS;
		return waitForStatusToChange(stackName, inProgressStatus);
	}
	
	public String waitForDeleteFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException {
		StackStatus inProgressStatus = StackStatus.DELETE_IN_PROGRESS;
		try {
			return waitForStatusToChange(stackName, inProgressStatus);
		}
		catch(com.amazonaws.AmazonServiceException awsException) {
			String errorCode = awsException.getErrorCode();
			if (errorCode.equals("ValidationError")) {
				return StackStatus.DELETE_COMPLETE.toString();
			}
			return StackStatus.DELETE_FAILED.toString();
		}	
	}

	private String waitForStatusToChange(String stackName, StackStatus inProgressStatus) 
			throws WrongNumberOfStacksException, InterruptedException {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		
		String status = inProgressStatus.toString();
		while (status.equals(inProgressStatus.toString())) {
			Thread.sleep(STATUS_CHECK_INTERVAL_MILLIS);
			DescribeStacksResult result = cfnClient.describeStacks(describeStacksRequest);
			List<Stack> stacks = result.getStacks();
			if (stacks.size()!=1) {
				throw new WrongNumberOfStacksException(1, stacks.size());
			}
			status = stacks.get(0).getStackStatus();			
		}
		return status;
	}
	
	public void deleteStack(String stackName) {
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		
		cfnClient.deleteStack(deleteStackRequest);	
	}

	private String loadFileContents(File file) throws IOException {
		return FileUtils.readFileToString(file, Charset.defaultCharset());
	}

	@Override
	public List<Parameter> fetchParametersFor(File file, String env) throws FileNotFoundException, IOException, InvalidParameterException {
		logger.info(String.format("Discover and populate parameters for %s and env %s", file.getAbsolutePath(), env));
		List<TemplateParameter> allParameters = validateTemplate(file);
		List<Parameter> matches = new LinkedList<Parameter>();
		for(TemplateParameter templateParam : allParameters) {
			String name = templateParam.getParameterKey();
			if (name.equals(PARAMETER_ENV)) {
				continue;
			}
			logger.info("Checking if parameter should be auto-populated, param name is " + name);
			String description = templateParam.getDescription();
			if (shouldPopulateFor(description)) {
				populateParameter(matches, name, description);
			}
		}
		return matches;
	}

	private void populateParameter(List<Parameter> matches, String parameterName, String parameterDescription)
			throws InvalidParameterException {
		String logicalId = parameterDescription.substring(PARAM_PREFIX.length());
		logger.info("Attempt to find physical ID for LogicalID: " + logicalId);
		String value = findPhysicalIdByLogicalId(logicalId);
		if (value==null) {
			logger.error(String.format("Failed to find physicalID to match logicalID: %s required for parameter: %s" + logicalId, parameterName));
			throw new InvalidParameterException(parameterName);
		}
		logger.info(String.format("Found physicalID: %s matching logicalID: %s Populating this into parameter %s", value, logicalId, parameterName));
		addParameterTo(matches, parameterName, value);
	}

	public String findPhysicalIdByLogicalId(String logicalId) {
		logger.info("Looking for resource matching logicalID:" + logicalId);
		List<Stack> stacks = getStacks();
		for(Stack stack : stacks) {
			String stackName = stack.getStackName();
			String maybeHaveId = findPhysicalIdByLogicalId(stackName, logicalId);
			if (maybeHaveId!=null) {
				logger.info(String.format("Found physicalID: %s for logical ID: %s", maybeHaveId, logicalId));
				return maybeHaveId;
			}
		}
		return null;
	}
	
	public String findPhysicalIdByLogicalId(String stackName, String logicalId) {	
		logger.info(String.format("Check stack %s for logical ID %s", stackName, logicalId));
		List<StackResource> resources = getResourcesForStack(stackName);
		for (StackResource resource : resources) {
			String candidateId = resource.getLogicalResourceId();
			if (candidateId.equals(logicalId)) {
				return resource.getPhysicalResourceId();
			}
		}
		return null;		
	}
	
	private List<Stack> getStacks() {
		// TODO cache stack resources to avoid slow network calls
		DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
		DescribeStacksResult results = cfnClient.describeStacks(describeStackRequest);
		List<Stack> stacks = results.getStacks();
		return stacks;
	}


	private List<StackResource> getResourcesForStack(String stackName) {
		// TODO cache stack resources to avoid slow network calls
		DescribeStackResourcesRequest request = new DescribeStackResourcesRequest();
		request.setStackName(stackName);
		DescribeStackResourcesResult results = cfnClient.describeStackResources(request);
		
		return results.getStackResources();
	}

	private boolean shouldPopulateFor(String description) {
		if (description==null) {
			return false;
		}
		return description.startsWith(PARAM_PREFIX);
	}


}

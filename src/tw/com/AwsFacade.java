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
import com.amazonaws.regions.Regions;
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

public class AwsFacade implements AwsProvider {
	private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	private static final String PARAM_PREFIX = "::";
	
	// TODO things we need to ID
	// logical ID
	// id subnet by cidr & vpc
	// id sg by TAG and VPC
	// id VPC by TAG
	
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 200;
	private static final String PARAMETER_ENV = "env";
	private AmazonCloudFormationClient cfnClient;
	private Region euRegion = Region.getRegion(Regions.EU_WEST_1);

	public AwsFacade(AWSCredentialsProvider credentialsProvider) {
		cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(euRegion);
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
		String contents = loadFileContents(file);
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		String stackName = createStackName(file, env);
		logger.info("Stackname is " + stackName);
		
		checkParameters(parameters);
		
		logger.info(String.format("Setting %s parameter to %s", PARAMETER_ENV, env));
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey(PARAMETER_ENV);
		envParameter.setParameterValue(env);
		parameters.add(envParameter);
		
		List<Parameter> autoPopulatedParametes = this.fetchParametersFor(file, env);
		for (Parameter autoPop : autoPopulatedParametes) {
			parameters.add(autoPop);
		}
		
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		
		logger.info("Making createStack call to AWS");
		cfnClient.createStack(createStackRequest);	
		return stackName;
	}

	private void checkParameters(Collection<Parameter> parameters) throws InvalidParameterException {
		for(Parameter param : parameters) {
			if (param.getParameterKey().equals(PARAMETER_ENV)) {
				logger.error("Attempt to overide autoset parameter called " + PARAMETER_ENV);
				throw new InvalidParameterException(PARAMETER_ENV);
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
				String logicalId = description.substring(PARAM_PREFIX.length());
				logger.info("Attempt to find physical ID for LogicalID: " + logicalId);
				String value = findPhysicalIdByLogicalId(logicalId);
				if (value==null) {
					logger.error(String.format("Failed to find physicalID to match logicalID: %s required for parameter: %s" + logicalId, name));
					throw new InvalidParameterException(name);
				}
				logger.info(String.format("Found physicalID: %s matching logicalID: %s Populating this into parameter %s", value, logicalId, name));
				Parameter match = new Parameter();
				match.setParameterKey(name);
				match.setParameterValue(value);
				matches.add(match);
			}
		}
		return matches;
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

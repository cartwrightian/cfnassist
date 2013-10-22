package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;

public class AwsFacade implements AwsProvider {
	
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
		ValidateTemplateRequest validateTemplateRequest = new ValidateTemplateRequest();
		validateTemplateRequest.setTemplateBody(templateBody);

		ValidateTemplateResult result = cfnClient
				.validateTemplate(validateTemplateRequest);
		return result.getParameters();
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
		// TODO logging
		String contents = loadFileContents(file);
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		String stackName = createStackName(file, env);
		
		checkParameters(parameters);
		
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey(PARAMETER_ENV);
		envParameter.setParameterValue(env);
		parameters.add(envParameter);
		
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		
		cfnClient.createStack(createStackRequest);	
		return stackName;
	}

	private void checkParameters(Collection<Parameter> parameters) throws InvalidParameterException {
		for(Parameter param : parameters) {
			if (param.getParameterKey().equals(PARAMETER_ENV)) {
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


}

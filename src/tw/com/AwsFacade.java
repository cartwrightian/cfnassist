package tw.com;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;
import com.amazonaws.services.storagegateway.model.ErrorCode;

public class AwsFacade implements AwsProvider {
	
	// TODO things we need to ID
	// logical ID
	// id subnet by cidr & vpc
	// id sg by TAG and VPC
	// id VPC by TAG
	// auto create stackname based on supplied filename?
	
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 200;
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
	
	public String applyTemplate(File file, String stackName, Collection<Parameter> parameters) throws FileNotFoundException, IOException {
		String contents = loadFileContents(file);
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		
		CreateStackResult result = cfnClient.createStack(createStackRequest);	
		return result.getStackId();
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

	private String loadFileContents(File file) throws FileNotFoundException,
			IOException {
		String contents;
		BufferedReader br = new BufferedReader(new FileReader(file));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append('\n');
				line = br.readLine();
			}
			contents = sb.toString();
		} finally {
			br.close();
		}
		return contents;
	}


}

package tw.com.providers;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.MonitorStackEvents;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MissingCapabilities;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CloudFormationClient {
	private static final Logger logger = LoggerFactory.getLogger(CloudFormationClient.class);

	private AmazonCloudFormationClient cfnClient;

	public CloudFormationClient(AmazonCloudFormationClient cfnClient) {
		this.cfnClient = cfnClient;
	}

	public Stack describeStack(String stackName) throws WrongNumberOfStacksException {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		
		DescribeStacksResult result = cfnClient.describeStacks(describeStacksRequest);
		List<Stack> stacks = result.getStacks();
		
		int numberOfStacks = stacks.size();
		if (numberOfStacks != 1) {
			logger.error("Wrong number of stacks found: " + numberOfStacks);
			throw new WrongNumberOfStacksException(1, numberOfStacks);
		}
		return stacks.get(0);
	}

	public List<StackEvent> describeStackEvents(String stackName) {
		DescribeStackEventsRequest request = new DescribeStackEventsRequest();
		request.setStackName(stackName);
		DescribeStackEventsResult result = cfnClient.describeStackEvents(request);
		return result.getStackEvents();
	}

	public List<TemplateParameter> validateTemplate(String contents) {
		ValidateTemplateRequest validateTemplateRequest = new ValidateTemplateRequest();
		validateTemplateRequest.setTemplateBody(contents);
		return cfnClient.validateTemplate(validateTemplateRequest).getParameters();
	}

	public void deleteStack(String stackName) {
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		logger.info("Requesting deletion of stack " + stackName);
		cfnClient.deleteStack(deleteStackRequest);	
	}

	public List<StackResource> describeStackResources(String stackName) {
		DescribeStackResourcesRequest request = new DescribeStackResourcesRequest();
		request.setStackName(stackName);
		DescribeStackResourcesResult results = cfnClient.describeStackResources(request);
		return results.getStackResources();
	}

	public List<Stack> describeAllStacks() {
		DescribeStacksResult results = cfnClient.describeStacks();

		return results.getStacks();
	}

	private Collection<Tag> createTagsForStack(ProjectAndEnv projectAndEnv, String commentTag) {	
		Collection<Tag> tags = new ArrayList<>();
		tags.add(createTag(AwsFacade.PROJECT_TAG, projectAndEnv.getProject()));
		tags.add(createTag(AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv()));
		if (projectAndEnv.hasBuildNumber()) {
			Integer number = projectAndEnv.getBuildNumber();
			tags.add(createTag(AwsFacade.BUILD_TAG, number.toString()));
		}
		if (!commentTag.isEmpty()) {
			logger.info(String.format("Adding %s: %s", AwsFacade.COMMENT_TAG, commentTag));
			tags.add(createTag(AwsFacade.COMMENT_TAG, commentTag));
		}
		return tags;
	}
	
	private Tag createTag(String key, String value) {
		Tag tag = new Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

	public StackNameAndId createStack(ProjectAndEnv projAndEnv,
			String contents, String stackName,
			Collection<Parameter> parameters, MonitorStackEvents monitor,
			String commentTag) throws CfnAssistException {
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setTemplateBody(contents);
		createStackRequest.setStackName(stackName);
		createStackRequest.setParameters(parameters);
		monitor.addMonitoringTo(createStackRequest);
		Collection<Tag> tags = createTagsForStack(projAndEnv, commentTag);
		createStackRequest.setTags(tags);
		
		if (projAndEnv.useCapabilityIAM()) {
			logger.info("Adding CAPABILITY_IAM to create request");
			List<String> capabilities = new ArrayList<>();
			capabilities.add("CAPABILITY_IAM");
			createStackRequest.setCapabilities(capabilities);
		}
		
		logger.info("Making createStack call to AWS");
		
		try {
			CreateStackResult result = cfnClient.createStack(createStackRequest);
			return new StackNameAndId(stackName, result.getStackId());
		}
		catch (InsufficientCapabilitiesException exception) {
			throw new MissingCapabilities(exception.getMessage());
		}
		
	}

	public StackNameAndId updateStack(String contents, Collection<Parameter> parameters,
			MonitorStackEvents monitor, String stackName) throws NotReadyException {
		
		logger.info("Will attempt to update stack: " + stackName);
		UpdateStackRequest updateStackRequest = new UpdateStackRequest();	
		updateStackRequest.setParameters(parameters);
		updateStackRequest.setStackName(stackName);
		updateStackRequest.setTemplateBody(contents);
		monitor.addMonitoringTo(updateStackRequest);
		UpdateStackResult result = cfnClient.updateStack(updateStackRequest);
		
		return new StackNameAndId(stackName,result.getStackId());
		
	}

}

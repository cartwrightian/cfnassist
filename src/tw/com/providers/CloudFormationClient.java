package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.*;
import tw.com.AwsFacade;
import tw.com.MonitorStackEvents;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.entity.Tagging;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MissingCapabilities;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CloudFormationClient {
	private static final Logger logger = LoggerFactory.getLogger(CloudFormationClient.class);

	private software.amazon.awssdk.services.cloudformation.CloudFormationClient cfnClient;

	public CloudFormationClient(software.amazon.awssdk.services.cloudformation.CloudFormationClient cfnClient) {
		this.cfnClient = cfnClient;
	}

	public Stack describeStack(String stackName) throws WrongNumberOfStacksException {
		DescribeStacksRequest describeStacksRequest = DescribeStacksRequest.builder().stackName(stackName).build();

		DescribeStacksResponse result = cfnClient.describeStacks(describeStacksRequest);
		List<Stack> stacks = result.stacks();
		
		int numberOfStacks = stacks.size();
		if (numberOfStacks != 1) {
			logger.error("Wrong number of stacks found: " + numberOfStacks);
			throw new WrongNumberOfStacksException(1, numberOfStacks);
		}
		return stacks.get(0);
	}

	public boolean stackExists(String stackName) {
		ListStacksRequest request = ListStacksRequest.builder().stackStatusFilters(
				StackStatus.CREATE_COMPLETE,
				StackStatus.ROLLBACK_COMPLETE,
				StackStatus.UPDATE_COMPLETE,
				StackStatus.UPDATE_ROLLBACK_COMPLETE,

				StackStatus.CREATE_IN_PROGRESS,
				StackStatus.UPDATE_IN_PROGRESS,
				StackStatus.ROLLBACK_IN_PROGRESS,
				StackStatus.UPDATE_ROLLBACK_IN_PROGRESS,
				StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS,

				StackStatus.CREATE_FAILED,
				StackStatus.DELETE_FAILED,
				StackStatus.ROLLBACK_FAILED,
				StackStatus.UPDATE_ROLLBACK_FAILED

		).build();
		ListStacksResponse result = cfnClient.listStacks(request);
		for (StackSummary summary : result.stackSummaries()) {
			if (summary.stackName().equals(stackName)) {
				return true;
			}
		}
		return false;
	}

	public StackStatus currentStatus(String stackName) throws WrongNumberOfStacksException {
		ListStacksResponse result = cfnClient.listStacks();
		for(StackSummary summary : result.stackSummaries()) {
			if (summary.stackName().equals(stackName)) {
				return summary.stackStatus();
			}
		}
		throw new WrongNumberOfStacksException(0,1);
	}

	public List<StackEvent> describeStackEvents(String stackName) {
		DescribeStackEventsRequest request = DescribeStackEventsRequest.builder().stackName(stackName).build();

		DescribeStackEventsResponse result = cfnClient.describeStackEvents(request);
		return result.stackEvents();
	}

	public List<TemplateParameter> validateTemplate(String contents) {
		ValidateTemplateRequest validateTemplateRequest = ValidateTemplateRequest.builder().templateBody(contents).build();

		return cfnClient.validateTemplate(validateTemplateRequest).parameters();
	}

	public void deleteStack(String stackName) {
		DeleteStackRequest deleteStackRequest = DeleteStackRequest.builder().stackName(stackName).build();
		logger.info("Requesting deletion of stack " + stackName);

		cfnClient.deleteStack(deleteStackRequest);
	}

	public List<StackResource> describeStackResources(String stackName) {
		DescribeStackResourcesRequest request = DescribeStackResourcesRequest.builder().stackName(stackName).build();

		DescribeStackResourcesResponse results = cfnClient.describeStackResources(request);
		return results.stackResources();
	}

	public List<Stack> describeAllStacks() {
		DescribeStacksResponse results = cfnClient.describeStacks();
		return results.stacks();
	}

	private Collection<Tag> createTagsForStack(ProjectAndEnv projectAndEnv, Tagging tagging) {
		Collection<Tag> tags = new ArrayList<>();
		tags.add(createTag(AwsFacade.PROJECT_TAG, projectAndEnv.getProject()));
		tags.add(createTag(AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv()));
		if (projectAndEnv.hasBuildNumber()) {
			Integer number = projectAndEnv.getBuildNumber();
			tags.add(createTag(AwsFacade.BUILD_TAG, number.toString()));
		}
        tagging.addTagsTo(tags);

		return tags;
	}
	
	private Tag createTag(String key, String value) {
		Tag tag = Tag.builder().key(key).value(value).build();
		return tag;
	}

	public StackNameAndId createStack(ProjectAndEnv projAndEnv,
			String contents, String stackName,
			Collection<Parameter> parameters, MonitorStackEvents monitor,
			Tagging tagging) throws CfnAssistException {
		Collection<Tag> tags = createTagsForStack(projAndEnv, tagging);

		CreateStackRequest.Builder createStackRequestBuilder = CreateStackRequest.builder().
			templateBody(contents).
				stackName(stackName).
				parameters(parameters).
				tags(tags);

		monitor.addMonitoringTo(createStackRequestBuilder);

		if (projAndEnv.useCapabilityIAM()) {
			logger.info("Adding CAPABILITY_IAM to create request");
			List<String> capabilities = new ArrayList<>();
			capabilities.add("CAPABILITY_IAM");
			createStackRequestBuilder.capabilitiesWithStrings(capabilities);
		}
		
		logger.info("Making createStack call to AWS");
		
		try {
			CreateStackResponse result = cfnClient.createStack(createStackRequestBuilder.build());
			return new StackNameAndId(stackName, result.stackId());
		}
		catch (InsufficientCapabilitiesException exception) {
			throw new MissingCapabilities(exception.getMessage());
		}
	}

	public StackNameAndId updateStack(String contents, Collection<Parameter> parameters,
			MonitorStackEvents monitor, String stackName) throws NotReadyException {
		
		logger.info("Will attempt to update stack: " + stackName);
		UpdateStackRequest.Builder updateStackRequest = UpdateStackRequest.builder().
		parameters(parameters).stackName(stackName).templateBody(contents);

		monitor.addMonitoringTo(updateStackRequest);

		UpdateStackResponse result = cfnClient.updateStack(updateStackRequest.build());

		return new StackNameAndId(stackName,result.stackId());
		
	}

}

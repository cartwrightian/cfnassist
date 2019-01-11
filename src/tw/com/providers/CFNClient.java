package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
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

public class CFNClient {
	private static final Logger logger = LoggerFactory.getLogger(CFNClient.class);

	private CloudFormationClient cloudFormationClient;

	public CFNClient(CloudFormationClient cloudFormationClient) {
		this.cloudFormationClient = cloudFormationClient;
	}

	public Stack describeStack(String stackName) throws WrongNumberOfStacksException {
		DescribeStacksRequest describeStacksRequest = DescribeStacksRequest.builder().stackName(stackName).build();

		DescribeStacksResponse result = cloudFormationClient.describeStacks(describeStacksRequest);
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
		ListStacksResponse result = cloudFormationClient.listStacks(request);
		for (StackSummary summary : result.stackSummaries()) {
			if (summary.stackName().equals(stackName)) {
				return true;
			}
		}
		return false;
	}

	public StackStatus currentStatus(String stackName) throws WrongNumberOfStacksException {
		ListStacksResponse result = cloudFormationClient.listStacks();
		for(StackSummary summary : result.stackSummaries()) {
			if (summary.stackName().equals(stackName)) {
				return summary.stackStatus();
			}
		}
		throw new WrongNumberOfStacksException(0,1);
	}

	public List<StackEvent> describeStackEvents(String stackName) {
		DescribeStackEventsRequest request = DescribeStackEventsRequest.builder().stackName(stackName).build();

		DescribeStackEventsResponse result = cloudFormationClient.describeStackEvents(request);
		return result.stackEvents();
	}

	public List<TemplateParameter> validateTemplate(String contents) {
		ValidateTemplateRequest validateTemplateRequest = ValidateTemplateRequest.builder().templateBody(contents).build();

		return cloudFormationClient.validateTemplate(validateTemplateRequest).parameters();
	}

	public void deleteStack(String stackName) {
		DeleteStackRequest deleteStackRequest = DeleteStackRequest.builder().stackName(stackName).build();
		logger.info("Requesting deletion of stack " + stackName);

		cloudFormationClient.deleteStack(deleteStackRequest);
	}

	public List<StackResource> describeStackResources(String stackName) {
		DescribeStackResourcesRequest request = DescribeStackResourcesRequest.builder().stackName(stackName).build();

		DescribeStackResourcesResponse results = cloudFormationClient.describeStackResources(request);
		return results.stackResources();
	}

	public List<Stack> describeAllStacks() {
		DescribeStacksResponse results = cloudFormationClient.describeStacks();
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

	public String detectDrift(String stackName) {
		logger.debug("Check drift for stack: " + stackName);
		DetectStackDriftRequest request = DetectStackDriftRequest.builder()
				.stackName(stackName)
				.build();
		DetectStackDriftResponse result = cloudFormationClient.detectStackDrift(request);
		String driftDetectionId = result.stackDriftDetectionId();
		logger.info(String.format("Drift detection id: %s for stackname %s", driftDetectionId, stackName));
		return driftDetectionId;
	}

	public boolean driftDetectionInProgress(String driftDetectionId) {
		DescribeStackDriftDetectionStatusResponse result = getDriftQueryStatus(driftDetectionId);
		return result.detectionStatus().equals(StackDriftDetectionStatus.DETECTION_IN_PROGRESS);
	}

	private DescribeStackDriftDetectionStatusResponse getDriftQueryStatus(String driftDetectionId) {
		DescribeStackDriftDetectionStatusRequest request = DescribeStackDriftDetectionStatusRequest.builder().
			stackDriftDetectionId(driftDetectionId)
			.build();
		return cloudFormationClient.describeStackDriftDetectionStatus(request);
	}

	public DriftStatus getDriftDetectionResult(String stackName, String detectionId) {
		DescribeStackDriftDetectionStatusResponse query = getDriftQueryStatus(detectionId);
		if (query.detectionStatus().equals(StackDriftDetectionStatus.DETECTION_COMPLETE)) {
			logger.info(String.format("Drift detection failed for query: %s status: %s reason: %s",
					detectionId,query.detectionStatus(), query.detectionStatusReason()));
		}
		return new DriftStatus(stackName, query.stackDriftStatus(), query.driftedStackResourceCount());
	}
	
	private Tag createTag(String key, String value) {
		return Tag.builder().key(key).value(value).build();
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
			CreateStackResponse result = cloudFormationClient.createStack(createStackRequestBuilder.build());
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

		UpdateStackResponse result = cloudFormationClient.updateStack(updateStackRequest.build());

		return new StackNameAndId(stackName,result.stackId());
		
	}


	public static class DriftStatus {
		private final StackDriftStatus stackDriftStatus;
		private final int driftedStackResourceCount;
		private final String stackName;

		public DriftStatus(String stackName, StackDriftStatus stackDriftStatus, int driftedStackResourceCount) {
			this.stackName = stackName;
			this.stackDriftStatus = stackDriftStatus;
			this.driftedStackResourceCount = driftedStackResourceCount;
		}

		public StackDriftStatus getStackDriftStatus() {
			return stackDriftStatus;
		}

		public int getDriftedStackResourceCount() {
			return driftedStackResourceCount;
		}

		public String getStackName() {
			return stackName;
		}

		@Override
		public String toString() {
			return "DriftStatus{" +
					"stackDriftStatus=" + stackDriftStatus +
					", driftedStackResourceCount=" + driftedStackResourceCount +
					", stackName='" + stackName + '\'' +
					'}';
		}
	}
}

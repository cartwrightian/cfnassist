package tw.com;

import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;

public class CfnRepository {
	private static final String AWS_EC2_INSTANCE_TYPE = "AWS::EC2::Instance";
	private static final Logger logger = LoggerFactory
			.getLogger(CfnRepository.class);
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 1000;
	private static final long MAX_CHECK_INTERVAL_MILLIS = 5000;
	private AmazonCloudFormationClient cfnClient;

	private StackResources stackResources;
	private List<StackEntry> stackEntries;
	private String project;

	public CfnRepository(AmazonCloudFormationClient cfnClient, String project) {
		this.cfnClient = cfnClient;
		stackResources = new StackResources();
		stackEntries = new LinkedList<StackEntry>();
		this.project = project;
	}
	
	public List<StackEntry> stacksMatchingEnvAndBuild(EnvironmentTag envTag, String buildNumber) {
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		List<StackEntry> candidateStacks = getAllStacksForProject();
		for (StackEntry entry : candidateStacks) {
			if (entry.getEnvTag().equals(envTag) && entry.getBuildNumber().equals(buildNumber)) {
				stacks.add(entry);
			}
		}
		return stacks;
	}

	public List<StackEntry> stacksMatchingEnv(EnvironmentTag envTag) {
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		List<StackEntry> candidateStacks = getAllStacksForProject();
		for (StackEntry entry : candidateStacks) {
			if (entry.getEnvTag().equals(envTag)) {
				stacks.add(entry);
			}
		}
		return stacks;
	}

	public String findPhysicalIdByLogicalId(EnvironmentTag envTag, String logicalId) {
		logger.info(String.format("Looking for resource matching logicalID: %s for %s",logicalId, envTag));
		List<StackEntry> stacks = stacksMatchingEnv(envTag);
		for (StackEntry stackEntry : stacks) {
			String stackName = stackEntry.getStack().getStackName();
			logger.debug(String.format("Checking stack %s for logical id %s",
					stackName, logicalId));
			String maybeHaveId = findPhysicalIdByLogicalId(envTag, stackName, logicalId);
			if (maybeHaveId != null) {
				logger.info(String.format(
						"Found physicalID: %s for logical ID: %s in stack %s",
						maybeHaveId, logicalId, stackName));
				return maybeHaveId;
			}
		}
		logger.warn("No match for logical ID was found");
		return null;
	}

	private String findPhysicalIdByLogicalId(EnvironmentTag envTag, String stackName, String logicalId) {
		logger.info(String.format(
				"Check Env %s and stack %s for logical ID %s", envTag,
				stackName, logicalId));

		try {
			List<StackResource> resources = getResourcesForStack(stackName);
			logger.debug(String.format("Found %s resources for stack %s",
					resources.size(), stackName));
			for (StackResource resource : resources) {
				String candidateId = resource.getLogicalResourceId();
				String physicalResourceId = resource.getPhysicalResourceId();
				logger.debug(String
						.format("Checking for match against resource phyId=%s logId=%s",
								physicalResourceId,
								resource.getLogicalResourceId()));
				if (candidateId.equals(logicalId)) {
					return physicalResourceId;
				}
			}
		} catch (AmazonServiceException exception) {
			logger.warn("Unable to check for resources, stack name: "
					+ stackName, exception);
		}

		return null;
	}

	private List<StackResource> getResourcesForStack(String stackName) {

		List<StackResource> resources = null;
		if (stackResources.containsStack(stackName)) {
			logger.info("Cache hit on stack resources for stack " + stackName);
			resources = stackResources.getStackResources(stackName);
		} else {
			logger.info("Cache miss, loading resources for stack " + stackName);
			DescribeStackResourcesRequest request = new DescribeStackResourcesRequest();
			request.setStackName(stackName);
			DescribeStackResourcesResult results = cfnClient
					.describeStackResources(request);

			resources = results.getStackResources();
			stackResources.addStackResources(stackName, resources);
		}
		return resources;
	}

	private List<StackEntry> getAllStacksForProject() {
		// TODO handle "next token"?

		if (stackEntries.size() == 0) {
			logger.info("No cached stacks, loading all stacks");
			DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
			DescribeStacksResult results = cfnClient.describeStacks(describeStackRequest);

			List<Stack> stacks = results.getStacks();
			populateEntriesIfProjectMatches(stacks);
			logger.info(String.format("Loaded %s stacks", stackEntries.size()));
		} else {
			logger.info("Cache hit on stacks");
		}
		return stackEntries;
	}

	private void populateEntriesIfProjectMatches(List<Stack> stacks) {
		logger.info(String.format("Populating stack entries for %s stacks",stacks.size()));
		for(Stack stack : stacks) {

			logger.info(String.format("Checking stack %s for tag", stack.getStackName()));
		
			List<Tag> tags = stack.getTags();
			int count = 3;
			String env = "";
			String proj = "";
			String build = "";
			for(Tag tag : tags) {
				String key = tag.getKey();
				String value = tag.getValue();
				if (key.equals(AwsFacade.ENVIRONMENT_TAG)) {
					env = value;
					count--;
				} else if (key.equals(AwsFacade.PROJECT_TAG)) {
					proj = value;
					count--;
				} else if (key.equals(AwsFacade.BUILD_TAG)) {
					build = value;
					count--;
				}
				if (count==0) break; // small optimisation 
			}
			addEntryIfProjectAndEnvMatches(stack, env, proj, build);
		}		
	}

	private void addEntryIfProjectAndEnvMatches(Stack stack, String env, String proj, String build) {
		String stackName = stack.getStackName();
		if (!proj.equals(project) || (env.isEmpty())) {
			logger.warn(String.format("Could not match expected tag (%s) for stack %s", AwsFacade.ENVIRONMENT_TAG, stackName));
		}
			
		logger.info(String.format("Stack %s matched %s and %s", stackName, env, proj));
		EnvironmentTag envTag = new EnvironmentTag(env);
		StackEntry entry = new StackEntry(envTag, stack);
		if (!build.isEmpty()) {
			logger.info(String.format("Saving associated build number (%s) into stack %s", build, stackName));
			entry.setBuildNumber(build);
		}
		if (stackEntries.contains(entry)) {
			stackEntries.remove(entry);
			logger.info("Replacing or Removing entry for stack " + stackName);
		}
		String stackStatus = stack.getStackStatus();
		stackEntries.add(entry);
		logger.info(String.format("Added stack %s matched, environment is %s, status was %s", 
				stackName, envTag, stackStatus));			 
	}

	public void updateRepositoryFor(StackId id) {
		logger.info("Update stack repository for stack: " + id);
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(id.getStackName());
		DescribeStacksResult results = cfnClient.describeStacks(describeStacksRequest);

		populateEntriesIfProjectMatches(results.getStacks());
	}

	public String waitForStatusToChangeFrom(String stackName,
			StackStatus currentStatus, List<String> aborts)
			throws WrongNumberOfStacksException, InterruptedException {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		long pause = STATUS_CHECK_INTERVAL_MILLIS;

		logger.info(String.format(
				"Waiting for stack %s to change FROM status %s", stackName,
				currentStatus));
		String status = currentStatus.toString();
		Stack stack = null;
		while (status.equals(currentStatus.toString())) {
			Thread.sleep(pause);
			DescribeStacksResult result = cfnClient
					.describeStacks(describeStacksRequest);
			List<Stack> stacks = result.getStacks();
			int numberOfStacks = stacks.size();
			if (numberOfStacks != 1) {
				logger.error("Wrong number of stacks found: " + numberOfStacks);
				throw new WrongNumberOfStacksException(1, numberOfStacks);
			}
			stack = stacks.get(0);
			status = stack.getStackStatus();
			logger.debug(String
					.format("Waiting for status of stack %s, status was %s, pause was %s",
							stackName, status, pause));
			if (pause < MAX_CHECK_INTERVAL_MILLIS) {
				pause = pause + STATUS_CHECK_INTERVAL_MILLIS;
			}
			if (aborts.contains(status)) {
				logger.error("Matched an abort status");
				break;
			}
		}
		logger.info(String
				.format("Stack status changed, status is now %s and reason (if any) was: '%s' ",
						status, stack.getStackStatusReason()));
		return status;
	}

	public List<StackEvent> getStackEvents(String stackName) {
		DescribeStackEventsRequest request = new DescribeStackEventsRequest();
		request.setStackName(stackName);
		DescribeStackEventsResult result = cfnClient
				.describeStackEvents(request);
		return result.getStackEvents();
	}

	public String getStackStatus(String stackName) {
		logger.info("Getting stack status for " + stackName);
		List<StackEntry> stacks = getAllStacksForProject();
		for (StackEntry entry : stacks) {
			Stack stack = entry.getStack();
			if (stack.getStackName().equals(stackName)) {
				// get latest status
				try {
					return getStackCurrentStatus(stackName);
				} catch (WrongNumberOfStacksException e) {
					logger.warn("Mismatch on stack status", e);
					return "";
				} catch (AmazonServiceException e) {
					logger.warn(e.toString());
					if (e.getStatusCode()==400) {
						return ""; // stack does not exist, perhaps a delete was in progress
					}
				}
			}
		}
		logger.warn("Failed to find stack status for :" + stackName);
		return "";
	}

	private String getStackCurrentStatus(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = describeStack(stackName);
		String stackStatus = stack.getStackStatus();
		logger.info(String.format("Got status %s for stack %s", stackStatus,
				stackName));
		return stackStatus;
	}

	public StackId getStackId(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = describeStack(stackName);
		String id = stack.getStackId();
		return new StackId(stackName, id);
	}

	private Stack describeStack(String stackName)
			throws WrongNumberOfStacksException {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult result = cfnClient
				.describeStacks(describeStacksRequest);
		if (result.getStacks().size() != 1) {
			throw new WrongNumberOfStacksException(1, result.getStacks().size());
		}
		return result.getStacks().get(0);
	}

	public List<String> getInstancesFor(ProjectAndEnv projAndEnv) {
		logger.info("Finding instances for " + projAndEnv);
		String buildNumber = "";
		if (projAndEnv.hasBuildNumber()) {
			buildNumber = projAndEnv.getBuildNumber();
			logger.info("Matching also on build number: " + buildNumber);
		}
		// fetch stacks that match proj, env and build
		List<StackEntry> stacks = stacksMatchingEnvAndBuild(projAndEnv.getEnvTag(), buildNumber);
		List<String> instanceIds = new LinkedList<String>();
		for (StackEntry entry : stacks) {
			if (entry.isLive()) {
				logger.info("Found stack :" + entry.getStackName());
				instanceIds.addAll(findInstancesForStack(entry.getStack()));
			} else {
				logger.info("Not adding stack :" + entry.getStackName());
			}
		}

		return instanceIds;
	}

	private List<String> findInstancesForStack(Stack stack) {
		List<String> instanceIds = new LinkedList<String>();
		List<StackResource> resources = getResourcesForStack(stack.getStackName());
		for (StackResource resource : resources) {
			String type = resource.getResourceType();
			if (type.equals(AWS_EC2_INSTANCE_TYPE)) {
				logger.info("Matched instance: "+ resource.getPhysicalResourceId());
				instanceIds.add(resource.getPhysicalResourceId());
			}
		}
		return instanceIds;
	}

	public Stack getStack(String stackName) throws WrongNumberOfStacksException {
		for(StackEntry entry : stackEntries) {
			if (entry.getStackName().equals(stackName)) {
				return entry.getStack();
			}
		}
		return describeStack(stackName);
	}

}

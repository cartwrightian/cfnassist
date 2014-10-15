package tw.com.repository;

import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.CheckStackExists;
import tw.com.StackCache;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class CfnRepository implements CheckStackExists {
	private static final Logger logger = LoggerFactory.getLogger(CfnRepository.class);

	private static final String AWS_EC2_INSTANCE_TYPE = "AWS::EC2::Instance";
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 1000;
	private static final long MAX_CHECK_INTERVAL_MILLIS = 5000;
	private AmazonCloudFormationClient cfnClient;

	private StackCache stackCache;
	public CfnRepository(AmazonCloudFormationClient cfnClient, String project) {
		this.cfnClient = cfnClient;
		stackCache = new StackCache(cfnClient, project);
	}
	
	public List<StackEntry> stacksMatchingEnvAndBuild(EnvironmentTag envTag, String buildNumber) {
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		
		for (StackEntry entry : stackCache.getEntries()) {
			if (entry.getEnvTag().equals(envTag) && entry.getBuildNumber().equals(buildNumber)) {
				stacks.add(entry);
			}
		}
		return stacks;
	}

	public List<StackEntry> stacksMatchingEnv(EnvironmentTag envTag) {
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		for (StackEntry entry : stackCache.getEntries()) {
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
			String stackName = stackEntry.getStackName();
			logger.debug(String.format("Checking stack %s for logical id %s", stackName, logicalId));
			String maybeHaveId = findPhysicalIdByLogicalId(envTag, stackName, logicalId);
			if (maybeHaveId != null) {
				logger.info(String.format(
						"Found physicalID: %s for logical ID: %s in stack %s", maybeHaveId, logicalId, stackName));
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
			List<StackResource> resources = stackCache.getResourcesForStack(stackName);
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

	@Override
	public boolean stackExists(String stackName) {
		logger.info("Check if stack exists for " + stackName);
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		
		try { //  throws if stack does not exist
			cfnClient.describeStacks(describeStacksRequest);
			return true;
		}
		catch (AmazonServiceException exception) { 
			if (exception.getStatusCode()==400) {
				return false;
			}
			throw exception;
		}
	}
	
	public String getStackStatus(String stackName) {
		logger.info("Getting stack status for " + stackName);
		for (StackEntry entry : stackCache.getEntries()) {
			Stack stack = entry.getStack();
			if (stack.getStackName().equals(stackName)) {
				// get latest status
				try {
					return getStackCurrentStatus(stackName);
				} catch (WrongNumberOfStacksException e) {
					logger.warn("Mismatch on stack status", e);
					return "";
				} catch (AmazonServiceException e) {
					logger.warn("Could not check status of stack " +stackName,e);
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

	public StackNameAndId getStackId(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = describeStack(stackName);
		String id = stack.getStackId();
		return new StackNameAndId(stackName, id);
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
		List<StackResource> resources = stackCache.getResourcesForStack(stack.getStackName());
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
		for(StackEntry entry : stackCache.getEntries()) {
			if (entry.getStackName().equals(stackName)) {
				return entry.getStack();
			}
		}
		return describeStack(stackName);
	}
	
	public List<StackEntry> getStacks() {
		return stackCache.getEntries();
	}

	public List<StackEntry> getStacks(EnvironmentTag envTag) {
		List<StackEntry> results = new LinkedList<StackEntry>();
		for(StackEntry entry : stackCache.getEntries()) {
			if (entry.getEnvTag().equals(envTag)) {
				results.add(entry);
			}
		}
		return results;	
	}

	public Stack updateRepositoryFor(StackNameAndId id) throws WrongNumberOfStacksException {
		return stackCache.updateRepositoryFor(id);		
	}

}

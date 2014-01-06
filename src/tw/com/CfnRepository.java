package tw.com;

import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final Logger logger = LoggerFactory.getLogger(CfnRepository.class);
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 500;
	private static final long MAX_CHECK_INTERVAL_MILLIS = 4000;
	private AmazonCloudFormationClient cfnClient;
	
	private StackResources stackResources;
	List<StackEntry> stackEntries;
	
	public CfnRepository(AmazonCloudFormationClient cfnClient) {
		this.cfnClient = cfnClient;
		stackResources = new StackResources();
		stackEntries = new LinkedList<StackEntry>();
	}
	
	public String findPhysicalIdByLogicalId(EnvironmentTag envTag, String logicalId) {
		
		logger.info(String.format("Looking for resource matching logicalID: %s for Env: %s", logicalId, envTag));
		List<StackEntry> stacks = getStacks();
		for(StackEntry stackEntry : stacks) {
			String stackName = stackEntry.getStack().getStackName();
			logger.debug(String.format("Checking stack %s for envTag %s", stackName, envTag));
			if (stackEntry.getEnvTag().equals(envTag)) {	
				logger.debug(String.format("Checking stack %s for logical id %s", stackName, logicalId));
				String maybeHaveId = findPhysicalIdByLogicalId(envTag, stackName, logicalId);
				if (maybeHaveId!=null) {
					logger.info(String.format("Found physicalID: %s for logical ID: %s in stack %s", maybeHaveId, logicalId, stackName));
					return maybeHaveId;
				}
			}
		}
		logger.warn("No match for logical ID was found");
		return null;
	}
	
	private String findPhysicalIdByLogicalId(EnvironmentTag envTag, String stackName, String logicalId) {	
		logger.info(String.format("Check Env %s and stack %s for logical ID %s", envTag, stackName, logicalId));

		List<StackResource> resources = getResourcesForStack(stackName);
		logger.debug(String.format("Found %s resources for stack %s", resources.size(), stackName));
		for (StackResource resource : resources) {
			String candidateId = resource.getLogicalResourceId();
			String physicalResourceId = resource.getPhysicalResourceId();
			logger.debug(String.format("Checking for match against resource phyId=%s logId=%s", physicalResourceId, 
					resource.getLogicalResourceId()));
			if (candidateId.equals(logicalId)) {
				return physicalResourceId;
			}
		}
		return null;		
	}
	
	private List<StackResource> getResourcesForStack(String stackName) {		

		List<StackResource> resources=null;
		if (stackResources.containsStack(stackName)) {
			logger.info("Cache hit on stack resources for stack " + stackName);
			resources = stackResources.getStackResources(stackName);
		} else {
			logger.info("Cache miss, loading resources for stack " + stackName);
			DescribeStackResourcesRequest request = new DescribeStackResourcesRequest();
			request.setStackName(stackName);
			DescribeStackResourcesResult results = cfnClient.describeStackResources(request);
			
			resources = results.getStackResources();
			stackResources.addStackResources(stackName, resources);
		}
		return resources;
	}
	
	private List<StackEntry> getStacks() {		
		// TODO handle "next token"?
		
		if (stackEntries.size()==0) {
			logger.info("No cached stacks, loading all stacks");
			DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
			DescribeStacksResult results = cfnClient.describeStacks(describeStackRequest);
			
			List<Stack> stacks = results.getStacks();
			populateEntries(stacks);
			logger.info(String.format("Loaded %s stacks", stackEntries.size()));
		} else {
			logger.info("Cache hit on stacks");
		}
		return stackEntries;
	}
	
	private void populateEntries(List<Stack> stacks) {
		logger.info(String.format("Populating stack entries for %s stacks",stacks.size()));
		for(Stack stack : stacks) {
			String stackName = stack.getStackName();
			logger.info(String.format("Checking stack %s for tag", stackName));
			List<Tag> tags = stack.getTags();
			boolean foundTag = false;
			for(Tag tag : tags) {
				if (tag.getKey().equals(AwsFacade.ENVIRONMENT_TAG))
				{
					EnvironmentTag envTag = new EnvironmentTag(tag.getValue());
					StackEntry entry = new StackEntry(envTag, stack);
					if (stackEntries.contains(entry)) {
						stackEntries.remove(entry);
						logger.info("Replacing entry for stack " + stackName);
					}
					stackEntries.add(entry);
					logger.info(String.format("Added stack %s matched, environment is %s", stackName, envTag));	
					foundTag = true;
					break;
				}
			}
			if (!foundTag) {
				logger.warn(String.format("Could not find expected tag (%s) for stack %s", AwsFacade.ENVIRONMENT_TAG, stackName));
			}
		}		
	}

	public void updateRepositoryFor(String stackName) {
		logger.info("Update stack repository for stack: " + stackName);
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult results = cfnClient.describeStacks();

		populateEntries(results.getStacks());
	}
	
	public String waitForStatusToChangeFrom(String stackName, StackStatus currentStatus) 
			throws WrongNumberOfStacksException, InterruptedException {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		long pause = STATUS_CHECK_INTERVAL_MILLIS;
		
		logger.info(String.format("Waiting for stack %s to change FROM status %s", stackName, currentStatus));
		String status = currentStatus.toString();
		Stack stack = null;
		while (status.equals(currentStatus.toString())) {
			Thread.sleep(pause);
			DescribeStacksResult result = cfnClient.describeStacks(describeStacksRequest);
			List<Stack> stacks = result.getStacks();
			int numberOfStacks = stacks.size();
			if (numberOfStacks!=1) {
				logger.error("Wrong number of stacks found: " + numberOfStacks);
				throw new WrongNumberOfStacksException(1, numberOfStacks);
			}
			stack = stacks.get(0);
			status = stack.getStackStatus();	
			logger.debug(String.format("Checking status of stack %s, status was %s", stackName, status));
			if (pause<MAX_CHECK_INTERVAL_MILLIS) {
				pause = pause + STATUS_CHECK_INTERVAL_MILLIS;
				logger.debug("Increase back off to " + pause);
			}
		}
		logger.info(String.format("Stack status changed, status is now %s and reason was: '%s' ", status, stack.getStackStatusReason()));
		return status;
	}

	public List<StackEvent> getStackEvents(String stackName) {
		DescribeStackEventsRequest request = new DescribeStackEventsRequest();
		request.setStackName(stackName);
		DescribeStackEventsResult result = cfnClient.describeStackEvents(request);
		return result.getStackEvents();
	}

}

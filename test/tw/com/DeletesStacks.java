package tw.com;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import tw.com.entity.StackNameAndId;

import java.util.LinkedList;
import java.util.List;

public class DeletesStacks {
	private static final int TIMEOUT_INCREMENT_MS = 500;
	private static final int INITIAL_TIMEOUT_MS = 1000;

	private static final Logger logger = LoggerFactory.getLogger(DeletesStacks.class);

	private CloudFormationClient cfnClient;
	private LinkedList<String> deleteIfPresent;
	private LinkedList<String> deleteIfPresentNonBlocking;

	public DeletesStacks(CloudFormationClient cfnClient) {
		this.cfnClient = cfnClient;
		deleteIfPresent = new LinkedList<>();
		deleteIfPresentNonBlocking = new LinkedList<>();
	}

	public DeletesStacks ifPresent(String stackName) {
		deleteIfPresent.add(stackName);
		return this;
	}
	
	public DeletesStacks ifPresent(StackNameAndId stackId) {
		return ifPresent(stackId.getStackName());	
	}

	public void act() {
		List<String> currentStacks = fetchCurrentStacks();
		List<String> deletionList = new LinkedList<>();
		List<String> deletionListNonBlocking = new LinkedList<>();
		for(String stackName : currentStacks) {
			if (deleteIfPresent.contains(stackName)) {
				deletionList.add(stackName);
			} else if (deleteIfPresentNonBlocking.contains(stackName)) {
				deletionListNonBlocking.add(stackName);
			}
		}
		try {
			if (deletionList.isEmpty() && deletionListNonBlocking.isEmpty()) {
				logger.info("No stacks need deleting");
			} else {
				requestDeletion(deletionListNonBlocking);
				deleteStacks(deletionList);
			}
		} catch (InterruptedException e) {
			logger.error("Exception",e);
		}
	}

	private List<String> fetchCurrentStacks() {
		List<String> current = new LinkedList<>();

		DescribeStacksResponse result = cfnClient.describeStacks();
		for(Stack stack : result.stacks()) {
			current.add(stack.stackName());
		}
		return current;
	}

	private void deleteStacks(List<String> deletionList) throws InterruptedException {
		if (deletionList.isEmpty()) {
			return;
		}
		
		requestDeletion(deletionList);
		
		List<String> present = fetchCurrentStacks();
		int count = EnvironmentSetupForTests.DELETE_RETRY_LIMIT;
		long timeout = INITIAL_TIMEOUT_MS;
		while (containsAnyOf(present,deletionList) && (count>0)) {
			logger.info(String.format("Waiting for stack deletion (check %s/%s). Present: %s Deleting: %s", 
					(EnvironmentSetupForTests.DELETE_RETRY_LIMIT-count), EnvironmentSetupForTests.DELETE_RETRY_LIMIT, 
					present.size(), deletionList.size()));
			Thread.sleep(timeout);
			count--;
			if (timeout<EnvironmentSetupForTests.DELETE_RETRY_MAX_TIMEOUT_MS) {
				timeout = timeout + TIMEOUT_INCREMENT_MS;
			}
			present = fetchCurrentStacks();
		}
		if (count==0) {
			logger.warn("Timed out waiting for deletions");
		} else {
			logger.info("Deleted stacks");
		}
		for(String unwanted : deletionList) {
			if (present.contains(unwanted)) {
				logger.error("Stack was not deleted yet: " + unwanted);
			}
		}	
	}

	private void requestDeletion(List<String> deletionList) {
		for(String stackName : deletionList) {
			DeleteStackRequest request = DeleteStackRequest.builder().stackName(stackName).build();
			//request.setStackName(stackName);
			cfnClient.deleteStack(request);
			logger.info("Requested deletion of " + stackName);
		}
	}

	private boolean containsAnyOf(List<String> present,
			List<String> deletionList) {
		for(String unwanted : deletionList) {
			if (present.contains(unwanted)) {
				return true;
			}
		}
		return false;
	}


}

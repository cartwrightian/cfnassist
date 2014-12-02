package tw.com;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.entity.DeletionPending;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.StackRepository;

import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;

public class PollingStackMonitor extends StackMonitor {
	private static final Logger logger = LoggerFactory.getLogger(PollingStackMonitor.class);
	private StackRepository cfnRepository;
	
	public PollingStackMonitor(StackRepository cfnRepository) {
		this.cfnRepository = cfnRepository;
	}

	@Override
	public String waitForCreateFinished(StackNameAndId stackId) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus {	
		String stackName = stackId.getStackName();
		String result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(CREATE_ABORTS));
		String expected = StackStatus.CREATE_COMPLETE.toString();
		if (!result.equals(expected)) {
			logger.error(String.format("Failed to create stack %s, status is %s", stackId, result));
			logStackEvents(cfnRepository.getStackEvents(stackName));
			throw new WrongStackStatus(stackId, expected,result);
		}
		return result;
	}
	
	@Override
	public String waitForRollbackComplete(StackNameAndId id) throws NotReadyException,
			 WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		String stackName = id.getStackName();
		String result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.ROLLBACK_IN_PROGRESS, Arrays.asList(ROLLBACK_ABORTS));
		String complete = StackStatus.ROLLBACK_COMPLETE.toString();
		if (!result.equals(complete)) {
			logger.error("Expected " + complete);
			logStackEvents(cfnRepository.getStackEvents(stackName));
			throw new WrongStackStatus(id, complete, result);
		}
		return result;
	}
	
	public String waitForDeleteFinished(StackNameAndId stackId) throws WrongNumberOfStacksException, InterruptedException {
		StackStatus initialStatus = StackStatus.DELETE_IN_PROGRESS;
		String result = StackStatus.DELETE_FAILED.toString();
		try {
			result = cfnRepository.waitForStatusToChangeFrom(stackId.getStackName(), initialStatus, Arrays.asList(DELETE_ABORTS));
		}
		catch(com.amazonaws.AmazonServiceException awsException) {
			logger.warn("Caught exception during status check", awsException);
			String errorCode = awsException.getErrorCode();
			// TODO should this be using statuscode=400?
			if (errorCode.equals("ValidationError")) {
				result = StackStatus.DELETE_COMPLETE.toString();
			} else {
				result = StackStatus.DELETE_FAILED.toString();
			}		
		}	
		
		if (!result.equals(StackStatus.DELETE_COMPLETE.toString())) {
			logger.error("Failed to delete stack, status is " + result);
			logStackEvents(cfnRepository.getStackEvents(stackId.getStackName()));
		}
		return result;
	}

	private void logStackEvents(List<StackEvent> stackEvents) {
		for(StackEvent event : stackEvents) {
			logger.info(event.toString());
		}	
	}

	@Override
	public void init() {
		// no op for polling monitor	
	}

	@Override
	public String waitForUpdateFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus {
		String stackName = id.getStackName();
		String result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.UPDATE_IN_PROGRESS, Arrays.asList(UPDATE_ABORTS));
		if (result.equals(StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS.toString())) {
			logger.info("Update now in cleanup status");
			result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS, Arrays.asList(UPDATE_ABORTS));
		}
		String complete = StackStatus.UPDATE_COMPLETE.toString();
		if (!result.equals(complete)) {
			logger.error("Expected " + complete);
			logStackEvents(cfnRepository.getStackEvents(id.getStackName()));
			throw new WrongStackStatus(id, complete, result);
		}
		return result;
	}

	@Override
	public List<String> waitForDeleteFinished(DeletionsPending pending, SetsDeltaIndex setDeltaIndex) {
		return monitorDeletions(pending, setDeltaIndex);
	}
	
	private List<String> monitorDeletions(DeletionsPending allPending, SetsDeltaIndex setDeltaIndex) {
		List<String> deletedOk = new LinkedList<String>();
		try {
			for(DeletionPending pending : allPending) {
				StackNameAndId id = pending.getStackId();
				logger.info("Now waiting for deletion of " + id);
				String status = waitForDeleteFinished(id );
				if (StackStatus.DELETE_COMPLETE.toString().equals(status)) {
					deletedOk.add(id.getStackName());
					int newDelta = pending.getDelta()-1;
					if (newDelta>=0) {
						logger.info("Resetting delta to " + newDelta);
						setDeltaIndex.setDeltaIndex(newDelta);
					}
				} else {
					break;
				}
			}
		}
		catch(CfnAssistException exception) {
			reportDeletionIssue(exception);
		} catch (InterruptedException exception) {
			reportDeletionIssue(exception);
		}
		return deletedOk;
	}

	private void reportDeletionIssue(Exception exception) {
		logger.error("Unable to wait for stack deletion ",exception);
		logger.error("Please manually check stack deletion and delta index values");
	}

	@Override
	public void addMonitoringTo(CreateStackRequest createStackRequest) {
		// does nothing in this implementation
	}

	@Override
	public void addMonitoringTo(UpdateStackRequest updateStackRequest)
			throws NotReadyException {
		// does nothing in this implementation	
	}

}

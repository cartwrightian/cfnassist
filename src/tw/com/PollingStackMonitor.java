package tw.com;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import tw.com.entity.DeletionPending;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.StackRepository;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


public class PollingStackMonitor extends StackMonitor {
	private static final Logger logger = LoggerFactory.getLogger(PollingStackMonitor.class);

	
	public PollingStackMonitor(StackRepository cfnRepository) {
		super(cfnRepository);
	}

	@Override
	public StackStatus waitForCreateFinished(StackNameAndId stackId) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus {
		String stackName = stackId.getStackName();
		StackStatus result = stackRepository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS,
				Arrays.asList(CREATE_ABORTS));
		if (!result.equals(StackStatus.CREATE_COMPLETE)) {
			logger.error(String.format("Failed to create stack %s, status is %s", stackId, result));
			logStackEvents(stackName);
			throw new WrongStackStatus(stackId,  StackStatus.CREATE_COMPLETE,result);
		}
		return result;
	}

	@Override
	public StackStatus waitForRollbackComplete(StackNameAndId id) throws
			WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		String stackName = id.getStackName();
		StackStatus result = stackRepository.waitForStatusToChangeFrom(stackName, StackStatus.ROLLBACK_IN_PROGRESS, Arrays.asList(ROLLBACK_ABORTS));
		if (!result.equals(StackStatus.ROLLBACK_COMPLETE)) {
			logger.error("Expected " + StackStatus.ROLLBACK_COMPLETE);
			logStackEvents(stackName);
			throw new WrongStackStatus(id, StackStatus.ROLLBACK_COMPLETE, result);
		}
		return result;
	}
	
	public StackStatus waitForDeleteFinished(StackNameAndId stackId) throws WrongNumberOfStacksException, InterruptedException {
		StackStatus initialStatus = StackStatus.DELETE_IN_PROGRESS;
		StackStatus result;

		result = stackRepository.waitForStatusToChangeFrom(stackId.getStackName(), initialStatus, Arrays.asList(DELETE_ABORTS));
		
		if (!result.equals(StackStatus.DELETE_COMPLETE)) {
			logger.error("Failed to delete stack, status is " + result);
			//final List<StackEvent> stackEvents = cfnRepository.getStackEvents(stackId.getStackName());
			logStackEvents(stackId.getStackName());
		}
		return result;
	}

	@Override
	public void init() {
		// no op for polling monitor	
	}

	@Override
	public StackStatus waitForUpdateFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus {
		String stackName = id.getStackName();
		StackStatus result = stackRepository.waitForStatusToChangeFrom(stackName, StackStatus.UPDATE_IN_PROGRESS, Arrays.asList(UPDATE_ABORTS));
		if (result.equals(StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS)) {
			logger.info("Update now in cleanup status");
			result = stackRepository.waitForStatusToChangeFrom(stackName, StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS, Arrays.asList(UPDATE_ABORTS));
		}
		StackStatus complete = StackStatus.UPDATE_COMPLETE;
		if (!result.equals(complete)) {
			logger.error("Expected " + complete);
			logStackEvents(id.getStackName());
			throw new WrongStackStatus(id, complete, result);
		}
		return result;
	}

	@Override
	public List<String> waitForDeleteFinished(DeletionsPending pending, SetsDeltaIndex setDeltaIndex) {
		return monitorDeletions(pending, setDeltaIndex);
	}
	
	private List<String> monitorDeletions(DeletionsPending allPending, SetsDeltaIndex setDeltaIndex) {
		List<String> deletedOk = new LinkedList<>();
		try {
			for(DeletionPending pending : allPending) {
				StackNameAndId id = pending.getStackId();
				logger.info("Now waiting for deletion of " + id);
				StackStatus status = waitForDeleteFinished(id );
				if (StackStatus.DELETE_COMPLETE.equals(status)) {
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
		catch(CfnAssistException | InterruptedException exception) {
			reportDeletionIssue(exception);
		}
		return deletedOk;
	}

	private void reportDeletionIssue(Exception exception) {
		logger.error("Unable to wait for stack deletion ",exception);
		logger.error("Please manually check stack deletion and delta index values");
	}

	@Override
	public void addMonitoringTo(CreateStackRequest.Builder createStackRequest) {
		// does nothing in this implementation
	}

	@Override
	public void addMonitoringTo(UpdateStackRequest.Builder updateStackRequest) {
		// does nothing in this implementation	
	}

}

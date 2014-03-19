package tw.com;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class PollingStackMonitor extends StackMonitor {
	private static final Logger logger = LoggerFactory.getLogger(PollingStackMonitor.class);
	private CfnRepository cfnRepository;
	
	public PollingStackMonitor(CfnRepository cfnRepository) {
		this.cfnRepository = cfnRepository;
	}

	@Override
	public String waitForCreateFinished(StackId stackId) throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed {	
		String stackName = stackId.getStackName();
		String result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(CREATE_ABORTS));
		if (!result.equals(StackStatus.CREATE_COMPLETE.toString())) {
			logger.error(String.format("Failed to create stack %s, status is %s", stackId, result));
			logStackEvents(cfnRepository.getStackEvents(stackName));
			throw new StackCreateFailed(stackId);
		}
		return result;
	}
	
	@Override
	public String waitForRollbackComplete(StackId id) throws NotReadyException,
			 WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		String stackName = id.getStackName();
		String result = cfnRepository.waitForStatusToChangeFrom(stackName, StackStatus.ROLLBACK_IN_PROGRESS, Arrays.asList(ROLLBACK_ABORTS));
		String complete = StackStatus.ROLLBACK_COMPLETE.toString();
		if (!result.equals(complete)) {
			logger.error("Expected " + complete);
			throw new WrongStackStatus(complete, result);
		}
		return result;
	}
	
	public String waitForDeleteFinished(StackId stackId) throws WrongNumberOfStacksException, InterruptedException {
		StackStatus requiredStatus = StackStatus.DELETE_IN_PROGRESS;
		String result = StackStatus.DELETE_FAILED.toString();
		try {
			result = cfnRepository.waitForStatusToChangeFrom(stackId.getStackName(), requiredStatus, Arrays.asList(DELETE_ABORTS));
		}
		catch(com.amazonaws.AmazonServiceException awsException) {
			String errorCode = awsException.getErrorCode();
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



}

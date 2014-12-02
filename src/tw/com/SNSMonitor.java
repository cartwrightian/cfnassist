package tw.com;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;

import tw.com.entity.DeletionPending;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.entity.StackNotification;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CheckStackExists;

public class SNSMonitor extends StackMonitor  {
	private static final Logger logger = LoggerFactory.getLogger(SNSMonitor.class);
	private static final int LIMIT = 50;
	private static final String STACK_RESOURCE_TYPE = "AWS::CloudFormation::Stack";
	
	private List<String> deleteAborts = Arrays.asList(DELETE_ABORTS);
	private NotificationProvider notifProvider;
	private CheckStackExists checkStackExists;

	public SNSMonitor(NotificationProvider eventSource, CheckStackExists checkStackExists) {
		this.notifProvider = eventSource;
		this.checkStackExists = checkStackExists;
	}

	@Override
	public String waitForCreateFinished(StackNameAndId stackId)
			throws WrongNumberOfStacksException, InterruptedException,
			NotReadyException, WrongStackStatus {
		guardForInit();
		return waitForStatus(stackId, StackStatus.CREATE_COMPLETE.toString(), Arrays.asList(CREATE_ABORTS));
	}
	
	@Override
	public String waitForDeleteFinished(StackNameAndId stackId)
			throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus {
		guardForInit();
		if (!checkStackExists.stackExists(stackId.getStackName())) {
			return StackStatus.DELETE_COMPLETE.toString(); // assume already gone
		}
		return waitForStatus(stackId, StackStatus.DELETE_COMPLETE.toString(), deleteAborts);
	}

	@Override
	public String waitForUpdateFinished(StackNameAndId stackId) throws WrongNumberOfStacksException, InterruptedException,
			WrongStackStatus, NotReadyException {
		guardForInit();
		return waitForStatus(stackId, StackStatus.UPDATE_COMPLETE.toString(), Arrays.asList(UPDATE_ABORTS));
	}
	
	@Override
	public String waitForRollbackComplete(StackNameAndId id) throws NotReadyException, InterruptedException, WrongStackStatus {
		guardForInit();
		return waitForStatus(id, StackStatus.ROLLBACK_COMPLETE.toString(), Arrays.asList(ROLLBACK_ABORTS));
	}
	
	@Override
	public List<String> waitForDeleteFinished(DeletionsPending pending, SetsDeltaIndex setsDeltaIndex) throws CfnAssistException {
		guardForInit();
		logger.info("Waiting for delete notifications");
		
		for(DeletionPending item : pending) {
			if (!checkStackExists.stackExists(item.getStackId().getStackName())) {
				logger.warn(String.format("Stack %s does not exist, assume already deleted", item.getStackId()));
				pending.markIdAsDeleted(item.getStackId().getStackId());
			}
		}
		
		int retryCount = 0;
		while ((retryCount<LIMIT) && (pending.hasMore())) {
			List<StackNotification>  notifications = notifProvider.receiveNotifications();
			if (notifications.size()==0) {
				logger.info("No messages received within timeout, increment try counter");
				retryCount++;
			} else {
				processNotificationsWithPendingDeletions(pending, notifications, setsDeltaIndex);
				notifications.clear();	
			}	
		}
		pending.updateDeltaIndex(setsDeltaIndex);
		return pending.getNamesOfDeleted();
	}

	private void processNotificationsWithPendingDeletions(DeletionsPending pending, List<StackNotification> notifications,
			SetsDeltaIndex setsDeltaIndex) throws WrongStackStatus {
		String deleteStatus = StackStatus.DELETE_COMPLETE.toString();
		for(StackNotification notification : notifications) {
			String resourceType = notification.getResourceType();
			if (resourceType.equals(STACK_RESOURCE_TYPE)) {
				String status = notification.getStatus();
				String stackName = notification.getStackName();
				String stackId = notification.getStackId();
				
				if (status.equals(deleteStatus)) {
					logger.info(String.format("Delete complete for stack name %s and id %s", stackName, stackId));
					pending.markIdAsDeleted(stackId);
				} else if (deleteAborts.contains(status)) {
					logger.error(String.format("Detected delete has failed for stackid %s name %s status was %s", stackId, stackName, status));
					throw new WrongStackStatus(new StackNameAndId(stackName, stackId), deleteStatus, status);
				}
				else {
					logger.info(String.format("Delete not yet complete for stack %s status is %s",stackName, notification.getStatus()));
				}			
			} else {
				logger.info(String.format("Got notification for resource type %s, status is %s", resourceType, notification.getStatus()));
			}
		}	
	}

	private String waitForStatus(StackNameAndId stackId, String requiredStatus, List<String> aborts) throws InterruptedException, WrongStackStatus, NotReadyException {
		logger.info(String.format("Waiting for stack %s to change to status %s", stackId, requiredStatus));
		int retryCount = 0;
		while (retryCount<LIMIT) {
			List<StackNotification> notifications = notifProvider.receiveNotifications(); // blocks and then times out if no messages received
			if (notifications.size()==0) {
				logger.info("No notifications received within timeout, increment try counter");
				retryCount++;
			} else {
				String status = processNotification(stackId, requiredStatus, aborts,notifications);	
				if (!status.isEmpty()) {
					return status;
				}
				notifications.clear();	
			}		
		}
		logger.error("Timed out waiting for status to change");
		throw new WrongStackStatus(stackId, requiredStatus, "timed out");
	}

	private String processNotification(StackNameAndId stackId, String requiredStatus, List<String> aborts, List<StackNotification> notifications)
			throws WrongStackStatus {
		for(StackNotification notification : notifications) {
			if (isMatchingStackNotif(notification, stackId)) {
				String status = notification.getStatus();
				if (status.equals(requiredStatus)) {
					return status;
				}
				if (aborts.contains(status)) {
					logger.error(String.format("Got an failure status %s while waiting for status %s", status, requiredStatus));
					throw new WrongStackStatus(stackId, requiredStatus, status);
				}
			}	
		}
		return "";
	}

	private void guardForInit() throws NotReadyException {
		if (!notifProvider.isInit()) {
			logger.error("Not initialised");
			throw new NotReadyException("SNSMonitor not initialised");
		}
	}

	private boolean isMatchingStackNotif(StackNotification notification, StackNameAndId stackId) {
		if (notification.getStackId().equals(stackId.getStackId())) {
			logger.info(String.format("Received notification for %s status was %s", notification.getResourceType(), notification.getStatus()));
			if (notification.getStatus().equals(StackStatus.CREATE_FAILED.toString())) {
				logger.warn(String.format("Failed to create resource of type %s reason was %s",notification.getResourceType(),notification.getStatusReason()));
			}
			return notification.getResourceType().equals(STACK_RESOURCE_TYPE); 
		} 
		
		logger.info(String.format("Notification did not match stackId, expected: %s was: %s", stackId.getStackId(), notification.getStackId()));		
		return false;
	}

	@Override
	public void init() throws MissingArgumentException, CfnAssistException, InterruptedException {
		notifProvider.init();
	}

	@Override
	public void addMonitoringTo(CreateStackRequest createStackRequest) throws NotReadyException {
		Collection<String> arns = getArns();
		createStackRequest.setNotificationARNs(arns);
	}

	@Override
	public void addMonitoringTo(UpdateStackRequest updateStackRequest) throws NotReadyException {
		Collection<String> arns = getArns();
		updateStackRequest.setNotificationARNs(arns);	
	}
	
	private Collection<String> getArns() throws NotReadyException {
		String arn = notifProvider.getSNSArn();
		logger.info("Setting arn for sns events to " + arn);
		Collection<String> arns = new LinkedList<String>();
		arns.add(arn);
		return arns;
	}


}

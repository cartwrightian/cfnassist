package tw.com.unit;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.cloudformation.model.StackStatus;

import tw.com.NotificationProvider;
import tw.com.SNSMonitor;
import tw.com.SetsDeltaIndex;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.entity.StackNotification;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.FailedToCreateQueueException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CheckStackExists;

@RunWith(EasyMockRunner.class)
public class TestSNSStackMonitor extends EasyMockSupport implements CheckStackExists, SetsDeltaIndex {
	
	private SNSMonitor monitor;
	private NotificationProvider eventSource;
	private StackNameAndId stackNameAndId;
	private String stackName;
	private String stackId;
	private boolean isStackFound;
	private int deltaIndexResult;
	private static final String STACK_RESOURCE_TYPE = "AWS::CloudFormation::Stack";
	private static final int LIMIT = 50;
	
	@Before 
	public void beforeEachTestRuns() {
		eventSource = createMock(NotificationProvider.class);
		monitor = new SNSMonitor(eventSource, this);
		stackName = "aStackName";
		stackId = "aStackId";
		stackNameAndId = new StackNameAndId(stackName, stackId);
	}
	
	@Test
	public void shouldTestWaitForStackCreationDoneEvents() throws InterruptedException, MissingArgumentException, CfnAssistException {	
		isStackFound= true;
		String inProgress = StackStatus.CREATE_IN_PROGRESS.toString();
		String complete = StackStatus.CREATE_COMPLETE.toString();	
		setExpectationsForInitAndReady();
		setEventStreamExpectations(inProgress, complete);
		
		replayAll();
		monitor.init();
		String result = monitor.waitForCreateFinished(stackNameAndId);
		assertEquals(complete, result);
		verifyAll();
	}
	
	@Test
	public void shouldTestWaitForStackDeletionDoneEvents() throws InterruptedException, MissingArgumentException, CfnAssistException {	
		isStackFound= true;
		String inProgress = StackStatus.DELETE_IN_PROGRESS.toString();
		String complete = StackStatus.DELETE_COMPLETE.toString();	
		setExpectationsForInitAndReady();
		setEventStreamExpectations(inProgress, complete);
		
		replayAll();
		monitor.init();
		String result = monitor.waitForDeleteFinished(stackNameAndId);
		assertEquals(complete, result);
		verifyAll();
	}
	
	@Test
	public void shouldTestWaitForStackDeletionStackAlreadyGone() throws InterruptedException, MissingArgumentException, CfnAssistException {	
		String complete = StackStatus.DELETE_COMPLETE.toString();	

		isStackFound= false;			
		setExpectationsForInitAndReady();
		
		replayAll();
		monitor.init();
		String result = monitor.waitForDeleteFinished(stackNameAndId);
		assertEquals(complete, result);
		verifyAll();
	}
	
	@Test
	public void shouldTestWaitForStackRollbackDoneEvents() throws InterruptedException, MissingArgumentException, CfnAssistException {	
		isStackFound= true;
		String inProgress = StackStatus.ROLLBACK_IN_PROGRESS.toString();
		String complete = StackStatus.ROLLBACK_COMPLETE.toString();	
		setExpectationsForInitAndReady();
		setEventStreamExpectations(inProgress, complete);
		
		replayAll();
		monitor.init();
		String result = monitor.waitForRollbackComplete(stackNameAndId);
		assertEquals(complete, result);
		verifyAll();
	}
	
	@Test 
	public void shouldThrowWhenExpectedStatusNotWithinTimeout() throws MissingArgumentException, InterruptedException, CfnAssistException {
		isStackFound= true;
		String inProgress = StackStatus.CREATE_IN_PROGRESS.toString();
		
		setExpectationsForInitAndReady();
		setExpectationsRepondInProgressUntilLimit(inProgress);
			
		replayAll();
		monitor.init();
		try {
			monitor.waitForRollbackComplete(stackNameAndId);
			fail("should have thrown");
		}
		catch(WrongStackStatus expectedException) {
			// noop
		}
		verifyAll();
	}

	private void setExpectationsRepondInProgressUntilLimit(String inProgress) throws NotReadyException {
		List<StackNotification> theEvents = new LinkedList<StackNotification>();
		addMatchingEvent(theEvents, inProgress, stackName, stackId);
		for(int count=0; count<=LIMIT; count++) {
			EasyMock.expect(eventSource.receiveNotifications()).andReturn(theEvents);
		}
	}
	
	@Test 
	public void shouldThrowIfNotifIsNotInit() throws WrongNumberOfStacksException, WrongStackStatus, InterruptedException, MissingArgumentException {
		isStackFound= true;
		EasyMock.expect(eventSource.isInit()).andReturn(false);
		
		replayAll();
		try {
			monitor.waitForCreateFinished(stackNameAndId);
			fail("should have thrown");
		}
		catch(NotReadyException expected) {
			// no op
		}
		verifyAll();
	}
	
	@Test
	public void ShouldCopeWithListOfNonExistanceStacksToDeleteSNS() throws CfnAssistException, MissingArgumentException, InterruptedException {
		isStackFound=false;

		setExpectationsForInitAndReady();
		
		replayAll();
		DeletionsPending pending = createPendingDeletes();
		monitor.init();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(2, deltaIndexResult);
		verifyAll();
	}
	
	@Test
	public void shouldMonitorMultiplePendingDeletes() throws InterruptedException, CfnAssistException, MissingArgumentException {
		isStackFound = true;
		String targetStatus = StackStatus.DELETE_COMPLETE.toString();
		
		DeletionsPending pending = createPendingDelete();
		
		eventSource.init();
		EasyMock.expectLastCall();
		EasyMock.expect(eventSource.isInit()).andReturn(true);
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackC", "id3");
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackB", "id2");
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackA", "id1");
	
		replayAll();
		monitor.init();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(0, deltaIndexResult);
		verifyAll();
	}
	
	@Test
	public void shouldMonitorMultiplePendingDeletesOutOfOrder() throws InterruptedException, CfnAssistException, MissingArgumentException {
		isStackFound = true;
		String targetStatus = StackStatus.DELETE_COMPLETE.toString();
		
		DeletionsPending pending = createPendingDelete();
		
		eventSource.init();
		EasyMock.expectLastCall();
		EasyMock.expect(eventSource.isInit()).andReturn(true);
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackB", "id2");
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackA", "id1");
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackC", "id3");
		
		replayAll();
		monitor.init();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(0, deltaIndexResult);
		verifyAll();
	}
	
	@Test
	public void shouldMonitorMultiplePendingDeletesNotAll() throws InterruptedException, CfnAssistException, MissingArgumentException {
		isStackFound = true;
		String targetStatus = StackStatus.DELETE_COMPLETE.toString();
		
		DeletionsPending pending = createPendingDelete();
		
		eventSource.init();
		EasyMock.expectLastCall();
		EasyMock.expect(eventSource.isInit()).andReturn(true);
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackB", "id2");
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackC", "id3");
		setExpectationsRepondInProgressUntilLimit(StackStatus.DELETE_IN_PROGRESS.toString());
	
		replayAll();
		monitor.init();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(1, deltaIndexResult);
		verifyAll();
	}
	
	
	// TOOD Deletions pending, highest or lowest??
	@Test
	public void shouldMonitorMultiplePendingDeletesSomeMissing() throws InterruptedException, CfnAssistException, MissingArgumentException {
		isStackFound = true;
		String targetStatus = StackStatus.DELETE_COMPLETE.toString();
		
		DeletionsPending pending = createPendingDelete();
		
		eventSource.init();
		EasyMock.expectLastCall();
		EasyMock.expect(eventSource.isInit()).andReturn(true);
		setEventStreamExpectations(StackStatus.DELETE_IN_PROGRESS.toString(), targetStatus, "stackB", "id2");
		setExpectationsRepondInProgressUntilLimit(StackStatus.DELETE_IN_PROGRESS.toString());
	
		replayAll();
		monitor.init();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(1, deltaIndexResult);
		verifyAll();
	}

	private DeletionsPending createPendingDelete() {
		DeletionsPending pending = new DeletionsPending();
		pending.add(3, new StackNameAndId("stackC", "id3"));
		pending.add(2, new StackNameAndId("stackB", "id2"));
		pending.add(1, new StackNameAndId("stackA", "id1"));
		return pending;
	}

	private DeletionsPending createPendingDeletes() {
		DeletionsPending pending = new DeletionsPending();
		pending.add(3, new StackNameAndId("alreadyGone1","11")); // final index should end up as 2
		pending.add(4, new StackNameAndId("alreadyGone2","12"));
		pending.add(5, new StackNameAndId("alreadyGone3","13"));
		return pending;
	}
	
	private void setEventStreamExpectations(String inProgress, String complete) throws NotReadyException {
		setEventStreamExpectations(inProgress, complete, stackName, stackId);	
	}

	private void setEventStreamExpectations(String inProgress, String complete, String theName, String theId) throws NotReadyException {
		List<StackNotification> theEvents = new LinkedList<StackNotification>();
		addMatchingEvent(theEvents, inProgress, theName, theId);
		addNonMatchingEvent(theEvents, complete, theName, theId);
		addMatchingEvent(theEvents, inProgress, theName, theId);
		addNonMatchingEvent(theEvents, inProgress, theName, theId);
		EasyMock.expect(eventSource.receiveNotifications()).andReturn(theEvents);
		
		List<StackNotification> moreEvents = new LinkedList<StackNotification>();
		moreEvents.addAll(theEvents);
		addNonMatchingEvent(moreEvents, complete, theName, theId);
		addMatchingEvent(moreEvents, complete, theName, theId);
		EasyMock.expect(eventSource.receiveNotifications()).andReturn(moreEvents);

	}

	private void setExpectationsForInitAndReady()
			throws MissingArgumentException, FailedToCreateQueueException, InterruptedException {
		eventSource.init();
		EasyMock.expectLastCall();
		EasyMock.expect(eventSource.isInit()).andReturn(true);
	}

	private void addMatchingEvent(List<StackNotification> theEvents, String status, String theName, String theId) {
		theEvents.add(new StackNotification(theName, status, theId, STACK_RESOURCE_TYPE, "reason"));
	}
	
	private void addNonMatchingEvent(List<StackNotification> theEvents,
			String status, String theName, String theId) {
		theEvents.add(new StackNotification(theName, status, theId, "someOtherType", "reason"));		
	}

	@Override
	public boolean stackExists(String stackName)
			throws WrongNumberOfStacksException {
		return isStackFound;
	}

	@Override
	public void setDeltaIndex(Integer newDelta) throws CannotFindVpcException {
		deltaIndexResult = newDelta;
	}

}

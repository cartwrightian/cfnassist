package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import tw.com.PollingStackMonitor;
import tw.com.SetsDeltaIndex;
import tw.com.StackMonitor;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CloudFormRepository;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestPollingStackMonitor extends EasyMockSupport implements SetsDeltaIndex {
	
	private CloudFormRepository cfnRepository;
	private PollingStackMonitor monitor;
	private String stackName;
	private StackNameAndId stackId;
	private int lastDeltaIndex;

	@BeforeEach
	public void beforeEachTestRuns() {
		cfnRepository = createMock(CloudFormRepository.class);
		monitor = new PollingStackMonitor(cfnRepository);
		
		stackName = "stackName";
		stackId = new StackNameAndId(stackName, "stackId");
	}
	
	@Test
	public void shouldWaitForCreationToComplete() throws WrongNumberOfStacksException, WrongStackStatus, InterruptedException {		
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.CREATE_ABORTS);
		StackStatus initialStatus = StackStatus.CREATE_IN_PROGRESS;
		StackStatus targetStatus = StackStatus.CREATE_COMPLETE;

		setRepoExcpetationsForSuccess(aborts, initialStatus, targetStatus);
		
		replayAll();
		StackStatus status = monitor.waitForCreateFinished(stackId);
		assertEquals(targetStatus, status);
		verifyAll();
	}
	
	@Test
	public void shouldWaitForDeleteToComplete() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.DELETE_ABORTS);
		StackStatus initialStatus = StackStatus.DELETE_IN_PROGRESS;
		StackStatus targetStatus = StackStatus.DELETE_COMPLETE;

		setRepoExcpetationsForSuccess(aborts, initialStatus, targetStatus);
		
		replayAll();
		StackStatus status = monitor.waitForDeleteFinished(stackId);
		assertEquals(targetStatus, status);
		verifyAll();
	}
	
	@Test
	public void shouldWaitForUpdateToComplete() throws WrongNumberOfStacksException, WrongStackStatus, InterruptedException {		
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.UPDATE_ABORTS);
		StackStatus initialStatus = StackStatus.UPDATE_IN_PROGRESS;
		StackStatus targetStatus = StackStatus.UPDATE_COMPLETE;

		setRepoExcpetationsForSuccess(aborts, initialStatus, targetStatus);
		
		replayAll();
		StackStatus status = monitor.waitForUpdateFinished(stackId);
		assertEquals(targetStatus, status);
		verifyAll();
	}
	
	@Test
	public void shouldWaitForUpdateToCompleteWithCleanUp() throws WrongNumberOfStacksException, WrongStackStatus, InterruptedException {		
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.UPDATE_ABORTS);
		StackStatus initialStatus = StackStatus.UPDATE_IN_PROGRESS;
		StackStatus targetStatus = StackStatus.UPDATE_COMPLETE;

		setRepoExcpetationsForSuccess(aborts, initialStatus, StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS);
		setRepoExcpetationsForSuccess(aborts, StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS, targetStatus);
		
		replayAll();
		StackStatus status = monitor.waitForUpdateFinished(stackId);
		assertEquals(targetStatus, status);
		verifyAll();
	}
	
	@Test
	public void shouldWaitForRollbackToComplete() throws WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.ROLLBACK_ABORTS);
		StackStatus initialStatus = StackStatus.ROLLBACK_IN_PROGRESS;
		StackStatus targetStatus = StackStatus.ROLLBACK_COMPLETE;

		setRepoExcpetationsForSuccess(aborts, initialStatus, targetStatus);
		
		replayAll();
		StackStatus status = monitor.waitForRollbackComplete(stackId);
		assertEquals(targetStatus, status);
		verifyAll();
	}
	
	@Test
	public void shouldThrowForCreationFailure() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.CREATE_ABORTS);
		setRepoExcpetationsForFailure(aborts, StackStatus.CREATE_IN_PROGRESS, StackStatus.CREATE_FAILED);
		
		replayAll();
		try {
			monitor.waitForCreateFinished(stackId);
			fail("exception expected");
		}
		catch(WrongStackStatus expectedException) {
			// noop
		}
		verifyAll();
	}
	
	@Test
	public void shouldThrowForCreationFailureDueToRollback() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.CREATE_ABORTS);
		setRepoExcpetationsForFailure(aborts, StackStatus.CREATE_IN_PROGRESS, StackStatus.ROLLBACK_IN_PROGRESS);
		
		replayAll();
		try {
			monitor.waitForCreateFinished(stackId);
			fail("exception expected");
		}
		catch(WrongStackStatus expectedException) {
			// noop
		}
		verifyAll();
	}
	
	@Test
	public void shouldThrowForUpdateFailure() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.UPDATE_ABORTS);
		setRepoExcpetationsForFailure(aborts, StackStatus.UPDATE_IN_PROGRESS, StackStatus.UPDATE_ROLLBACK_COMPLETE);
		
		replayAll();
		try {
			monitor.waitForUpdateFinished(stackId);
			fail("exception expected");
		}
		catch(WrongStackStatus expectedException) {
			// noop
		}
		verifyAll();
	}
	
	@Test
	public void shouldThrowForRollbackFailure() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.ROLLBACK_ABORTS);
		setRepoExcpetationsForFailure(aborts, StackStatus.ROLLBACK_IN_PROGRESS, StackStatus.ROLLBACK_FAILED);
		
		replayAll();
		try {
			monitor.waitForRollbackComplete(stackId);
			fail("exception expected");
		}
		catch(WrongStackStatus expectedException) {
			// noop
		}
		verifyAll();
	}
	
	@Test
	public void shouldReturnStatusDeletionFailure() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.DELETE_ABORTS);
		setRepoExcpetationsForFailure(aborts, StackStatus.DELETE_IN_PROGRESS, StackStatus.DELETE_FAILED);
		
		replayAll();		
		StackStatus result = monitor.waitForDeleteFinished(stackId);
		assertEquals(StackStatus.DELETE_FAILED, result);
		verifyAll();
	}
	
	@Test
	public void shouldMonitorMultiplePendingDeletes() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.DELETE_ABORTS);
		StackStatus targetStatus = StackStatus.DELETE_COMPLETE;

		DeletionsPending pending = new DeletionsPending();
		pending.add(3, new StackNameAndId("stackC", "id3"));
		pending.add(2, new StackNameAndId("stackB", "id2"));
		pending.add(1, new StackNameAndId("stackA", "id1"));
		
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom("stackC", StackStatus.DELETE_IN_PROGRESS, aborts))
			.andReturn(targetStatus);
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom("stackB", StackStatus.DELETE_IN_PROGRESS, aborts))
		.andReturn(targetStatus);
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom("stackA", StackStatus.DELETE_IN_PROGRESS, aborts))
		.andReturn(targetStatus);
		
		replayAll();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(0, lastDeltaIndex);
		verifyAll();
	}
	
	@Test
	public void shouldMonitorMultiplePendingDeletesAndLeaveIndexCorrectOnFailure() throws WrongNumberOfStacksException, InterruptedException {
		List<StackStatus> aborts =  Arrays.asList(StackMonitor.DELETE_ABORTS);
		StackStatus targetStatus = StackStatus.DELETE_COMPLETE;

		DeletionsPending pending = new DeletionsPending();
		pending.add(3, new StackNameAndId("stackC", "id3"));
		pending.add(2, new StackNameAndId("stackB", "id2"));
		pending.add(1, new StackNameAndId("stackA", "id1"));
		
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom("stackC", StackStatus.DELETE_IN_PROGRESS, aborts))
			.andReturn(targetStatus);
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom("stackB", StackStatus.DELETE_IN_PROGRESS, aborts))
			.andReturn(StackStatus.DELETE_FAILED);
		List<StackEvent> events = new LinkedList<>();
		EasyMock.expect(cfnRepository.getStackEvents("stackB")).andReturn(events);
	
		replayAll();
		monitor.waitForDeleteFinished(pending, this);
		assertEquals(2, lastDeltaIndex);
		verifyAll();
	}
	
	
	private void setRepoExcpetationsForSuccess(List<StackStatus> aborts,
			StackStatus initialStatus, StackStatus targetStatus)
			throws WrongNumberOfStacksException, InterruptedException {
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom(stackName, initialStatus, aborts))
			.andReturn(targetStatus);
	}
	
	private void setRepoExcpetationsForFailure(List<StackStatus> aborts,
			StackStatus initialStatus, StackStatus failureStatus) throws WrongNumberOfStacksException, InterruptedException {
		EasyMock.expect(cfnRepository.waitForStatusToChangeFrom(stackName, initialStatus, aborts)).
				andReturn(failureStatus);
		List<StackEvent> events = new LinkedList<>();
		EasyMock.expect(cfnRepository.getStackEvents(stackName)).andReturn(events);
	}

	@Override
	public void setDeltaIndex(Integer newDelta) {
		this.lastDeltaIndex = newDelta;
		
	}

}

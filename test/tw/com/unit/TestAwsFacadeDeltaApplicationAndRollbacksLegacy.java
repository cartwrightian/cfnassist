package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.*;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.*;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestAwsFacadeDeltaApplicationAndRollbacksLegacy extends UpdateStackExpectations {
	private AwsFacade aws;
	private NotificationSender notificationSender;
	private IdentityProvider identityProvider;

	private static final String THIRD_FILE = "03createRoutes.json";
	
	@Before
	public void beforeEachTestRuns() throws IOException {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createStrictMock(VpcRepository.class);
		ELBRepository elbRepository = createMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
		notificationSender = createStrictMock(NotificationSender.class);
		identityProvider = createStrictMock(IdentityProvider.class);

		String regionName = EnvironmentSetupForTests.getRegion().getName();

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, regionName);
		
		deleteFile(THIRD_FILE);
	}

	@Test
	public void shouldRollbackFilesInAFolder() throws CfnAssistException {
		String stackA = "CfnAssistTest01createSubnet";
		StackNameAndId stackANameAndId = new StackNameAndId(stackA, "id1");
		String stackB = "CfnAssistTest02createAcls";
		StackNameAndId stackBNameAndId = new StackNameAndId(stackB, "id2");

		SetDeltaIndexForProjectAndEnv setDeltaIndexForProjectAndEnv = new SetDeltaIndexForProjectAndEnv(projectAndEnv,vpcRepository);

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		EasyMock.expect(cfnRepository.getStackNameAndId(stackB)).andReturn(stackBNameAndId);
		cfnRepository.deleteStack(stackB);
		EasyMock.expectLastCall();
		EasyMock.expect(cfnRepository.getStackNameAndId(stackA)).andReturn(stackANameAndId);
		cfnRepository.deleteStack(stackA);
		EasyMock.expectLastCall();
		EasyMock.expect(vpcRepository.getSetsDeltaIndexFor(projectAndEnv)).
			andReturn(setDeltaIndexForProjectAndEnv);
		
		DeletionsPending pending = new DeletionsPending();
		pending.add(2, stackBNameAndId);
		pending.add(1, stackANameAndId);
	
		List<String> deletedStacks = new LinkedList<>();
		deletedStacks.add(stackB);
		deletedStacks.add(stackA);
		
		EasyMock.expect(monitor.waitForDeleteFinished(pending, setDeltaIndexForProjectAndEnv)).andReturn(deletedStacks);
		
		replayAll();
		List<String> result = aws.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv);
		verifyAll();
		assertEquals(2, result.size());
		assertTrue(result.contains(stackA));
		assertTrue(result.contains(stackB));
	}


	@Test
	public void shouldRollbackFilesInAFolderWithUpdate() throws CfnAssistException {
		String stackA = "CfnAssistTest01createSubnet";
		StackNameAndId stackANameAndId = new StackNameAndId(stackA, "id1");
		
		SetDeltaIndexForProjectAndEnv setDeltaIndexForProjectAndEnv = new SetDeltaIndexForProjectAndEnv(projectAndEnv,vpcRepository);

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		EasyMock.expect(cfnRepository.getStackNameAndId(stackA)).andReturn(stackANameAndId);

		cfnRepository.deleteStack(stackA);
		EasyMock.expectLastCall();
		EasyMock.expect(vpcRepository.getSetsDeltaIndexFor(projectAndEnv)).andReturn(setDeltaIndexForProjectAndEnv);
		
		DeletionsPending pending = new DeletionsPending();
		pending.add(1, stackANameAndId);
	
		List<String> deletedStacks = new LinkedList<>();
		deletedStacks.add(stackA);
		
		EasyMock.expect(monitor.waitForDeleteFinished(pending, setDeltaIndexForProjectAndEnv)).andReturn(deletedStacks);
		
		replayAll();
		List<String> result = aws.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString(), projectAndEnv);
		verifyAll();
		assertEquals(1, result.size());
		assertTrue(result.contains(stackA));
	}

	@Test
	public void shouldStepBackLastChangeInFolderOnAVpc() throws CfnAssistException {
		String stackB = "CfnAssistTest02createAcls";
		StackNameAndId stackBNameAndId = new StackNameAndId(stackB, "id2");

		SetDeltaIndexForProjectAndEnv setDeltaIndexForProjectAndEnv = new SetDeltaIndexForProjectAndEnv(projectAndEnv,vpcRepository);

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		EasyMock.expect(cfnRepository.getStackNameAndId(stackB)).andReturn(stackBNameAndId);
		cfnRepository.deleteStack(stackB);
		EasyMock.expectLastCall();
		
		EasyMock.expect(vpcRepository.getSetsDeltaIndexFor(projectAndEnv)).andReturn(setDeltaIndexForProjectAndEnv);
		
		DeletionsPending pending = new DeletionsPending();
		pending.add(2, stackBNameAndId);
	
		List<String> deletedStacks = new LinkedList<>();
		deletedStacks.add(stackB);
		
		EasyMock.expect(monitor.waitForDeleteFinished(pending, setDeltaIndexForProjectAndEnv)).andReturn(deletedStacks);
		
		replayAll();
		List<String> result = aws.stepbackLastChangeFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv);
		verifyAll();
		assertEquals(1, result.size());
		assertTrue(result.contains(stackB));
	}
	
	@Test
	public void shouldStepBackLastChangeOnAVpcWhenFileIsADelta() throws CfnAssistException {

		SetDeltaIndexForProjectAndEnv setDeltaIndexForProjectAndEnv = new SetDeltaIndexForProjectAndEnv(projectAndEnv,vpcRepository);

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		EasyMock.expect(vpcRepository.getSetsDeltaIndexFor(projectAndEnv)).andReturn(setDeltaIndexForProjectAndEnv);
		vpcRepository.setVpcIndexTag(projectAndEnv, "1");
		EasyMock.expectLastCall();
		
		replayAll();
		List<String> result = aws.stepbackLastChangeFromFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString(), projectAndEnv);
		verifyAll();
		assertEquals(0, result.size());
	}

	@Test
	public void shouldHandleStepBackWhenNotFileAvailable() throws CfnAssistException {

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("3"); // higher than file available
		
		replayAll();
		List<String> result = aws.stepbackLastChangeFromFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString(), projectAndEnv);
		verifyAll();
		assertEquals(0, result.size());
	}
	
	@Test
	public void shouldHandleRollBackWhenNotFileAvailable() throws CannotFindVpcException {

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("3"); // higher than file available
		
		replayAll();
		try {
			aws.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString(), projectAndEnv);
			fail("should have thrown");
		}
		catch(CfnAssistException expectedException) {
			// no op
		}
		verifyAll();
	}

	private void deleteFile(String filename) throws IOException {
		Path destFile = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, filename);
		Files.deleteIfExists(destFile);	
	}

}

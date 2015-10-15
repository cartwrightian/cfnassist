package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.identitymanagement.model.User;
import org.apache.commons.io.FileUtils;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.*;
import tw.com.entity.*;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestAwsFacadeDeltaApplicationAndRollbacks extends EasyMockSupport {
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private MonitorStackEvents monitor;
	private CloudRepository cloudRepository;
	private NotificationSender notificationSender;
	private IdentityProvider identityProvider;
	private User user;
	
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
		user = new User("path", "userName", "userId", "arn", new Date());

		String regionName = EnvironmentSetupForTests.getRegion().getName();

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, regionName);
		
		deleteFile(THIRD_FILE);
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParameters() throws IOException, InterruptedException, CfnAssistException {
		List<File> files = loadFiles(new File(FilesForTesting.ORDERED_SCRIPTS_FOLDER));

        EasyMock.expect(cloudRepository.getZones("eu-west-1")).andStubReturn(new HashMap<>());
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("0");
		setExpectationsForValidationPass(files);
		// processing pass
		Integer count = 1;
		for(File file : files) {
			setExpectationsForFile(count, file);	
			count++;
		}
		
		replayAll();
		ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(files.size(), result.size());	
		validateStacksCreated(files, 1, result);
		verifyAll();	
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParametersOnlyNeeded() throws IOException, InterruptedException, CfnAssistException {
		List<File> allFiles = loadFiles(new File(FilesForTesting.ORDERED_SCRIPTS_FOLDER));
		List<File> files = allFiles.subList(1, 2);

        EasyMock.expect(cloudRepository.getZones("eu-west-1")).andReturn(new HashMap<>());
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("1");
		setExpectationsForValidationPass(allFiles);
		// processing pass
		Integer count = 2;
		for(File file : files) {
			setExpectationsForFile(count, file);	
			count++;
		}
		
		replayAll();
		ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(files.size(), result.size());
		validateStacksCreated(files, 2, result);
		verifyAll();	
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParametersNoneNeeded() throws IOException, InterruptedException, CfnAssistException {
		List<File> allFiles = loadFiles(new File(FilesForTesting.ORDERED_SCRIPTS_FOLDER));
		
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		setExpectationsForValidationPass(allFiles);
	
		replayAll();
		ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(0, result.size());
		verifyAll();	
	}
	
	@Test
	public void shouldApplyNewFileAsNeeded() throws IOException, InterruptedException, CfnAssistException {
		List<File> originalFiles = loadFiles(new File(FilesForTesting.ORDERED_SCRIPTS_FOLDER));

        EasyMock.expect(cloudRepository.getZones("eu-west-1")).andReturn(new HashMap<>());
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		// validation pass does all files
		setExpectationsForValidationPass(originalFiles);
		// second run set up
		Path currentPath = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "holding", THIRD_FILE);
		File newFile = new File(currentPath.toString());
		LinkedList<File> newFiles = new LinkedList<>();
		newFiles.addAll(originalFiles);
		newFiles.add(newFile);
		// second run expectations
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
		setExpectationsForValidationPass(newFiles);
		setExpectationsForFile(3, newFile);
	
		replayAll();
		ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(0, result.size());
		copyInFile(THIRD_FILE);
		result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(1, result.size());
		verifyAll();	
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
	public void shouldRollbackFilesInAFolderWithDeltas() throws CfnAssistException {
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
		List<String> result = aws.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER.toString(), projectAndEnv);
		verifyAll();
		assertEquals(1, result.size());
		assertTrue(result.contains(stackA));
	}
	
	@Test
	public void shouldStepBackLastChangeOnAVpc() throws CfnAssistException {
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
		List<String> result = aws.stepbackLastChange(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv);
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
		List<String> result = aws.stepbackLastChange(FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER.toString(), projectAndEnv);
		verifyAll();
		assertEquals(0, result.size());
	}

	@Test
	public void shouldHandleStepBackWhenNotFileAvailable() throws CfnAssistException {

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("3"); // higher than file available
		
		replayAll();
		List<String> result = aws.stepbackLastChange(FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER.toString(), projectAndEnv);
		verifyAll();
		assertEquals(0, result.size());
	}
	
	@Test
	public void shouldHandleRollBackWhenNotFileAvailable() throws CannotFindVpcException {

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("3"); // higher than file available
		
		replayAll();
		try {
			aws.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER.toString(), projectAndEnv);
			fail("should have thrown");
		}
		catch(CfnAssistException expectedException) {
			// no op
		}
		verifyAll();
	}
	
	
	private void setExpectationsForValidationPass(List<File> allFiles)
			throws IOException {
		for(File file : allFiles) {
			String templateContents = EnvironmentSetupForTests.loadFile(file.getAbsolutePath());
			List<TemplateParameter> templateParameters = new LinkedList<>();
			EasyMock.expect(cfnRepository.validateStackTemplate(templateContents)).andReturn(templateParameters);
		}
	}

	private void validateStacksCreated(List<File> files, Integer count,
			ArrayList<StackNameAndId> result) {
		for(File file : files) {
			String expectedName = aws.createStackName(file, projectAndEnv);
			StackNameAndId expected = new StackNameAndId(expectedName, count.toString());
			assertTrue(result.contains(expected));
			count++;
		}
	}

	private void setExpectationsForFile(Integer count, File file) throws IOException, CfnAssistException, InterruptedException {
		String templateContents = EnvironmentSetupForTests.loadFile(file.getAbsolutePath());
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();
		String stackName = aws.createStackName(file, projectAndEnv);
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, count.toString());

		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc());
		EasyMock.expect(cfnRepository.validateStackTemplate(templateContents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");
		Tagging tagging = new Tagging();
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, templateContents, stackName, creationParameters, monitor, tagging))
			.andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, StackStatus.CREATE_COMPLETE.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification )).andReturn("sentMessageId");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(
				new Stack().withStackId(count.toString()).withStackName(stackName));
		vpcRepository.setVpcIndexTag(projectAndEnv, count.toString());
	}
	
	private List<File> loadFiles(File folder) {
		FilenameFilter jsonFilter = new JsonExtensionFilter();
		File[] files = folder.listFiles(jsonFilter);
		Arrays.sort(files); // place in lexigraphical order
		return Arrays.asList(files);
	}
	
	private Path copyInFile(String filename) throws IOException {
		Path srcFile = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "holding", filename);
		Path destFile = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, filename);
		Files.deleteIfExists(destFile);

		FileUtils.copyFile(srcFile.toFile(), destFile.toFile());
		return destFile;
	}
	
	private void deleteFile(String filename) throws IOException {
		Path destFile = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, filename);
		Files.deleteIfExists(destFile);	
	}

}

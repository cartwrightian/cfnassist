package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.Vpc;
import com.amazonaws.services.identitymanagement.model.User;
import org.apache.commons.io.FileUtils;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.*;
import tw.com.entity.*;
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
public class TestAwsFacadeDeltaApplicationAndRollbacks extends UpdateStackExpectations {
	private AwsFacade aws;
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

		LogRepository logRepository = createStrictMock(LogRepository.class);
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, logRepository);
		
		deleteFile(THIRD_FILE);
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParameters() throws IOException, InterruptedException, CfnAssistException {
		List<File> files = loadFiles(new File(FilesForTesting.ORDERED_SCRIPTS_FOLDER));

        EasyMock.expect(cloudRepository.getZones()).andStubReturn(new HashMap<>());
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("0");
		setExpectationsForValidationPass(files);
		// processing pass
		Integer count = 1;
		for(File file : files) {
			setExpectationsForFile(count, file, new LinkedList<>());
			count++;
		}
		
		replayAll();
		ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER,
                projectAndEnv, new LinkedList<>());
		assertEquals(files.size(), result.size());	
		validateStacksCreated(files, 1, result);
		verifyAll();	
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParametersOnlyNeeded() throws IOException, InterruptedException, CfnAssistException {
		List<File> allFiles = loadFiles(new File(FilesForTesting.ORDERED_SCRIPTS_FOLDER));
		List<File> files = allFiles.subList(1, 2);

        EasyMock.expect(cloudRepository.getZones()).andReturn(new HashMap<>());
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("1");
		setExpectationsForValidationPass(allFiles);
		// processing pass
		Integer count = 2;
		for(File file : files) {
			setExpectationsForFile(count, file, new LinkedList<>());
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

        EasyMock.expect(cloudRepository.getZones()).andReturn(new HashMap<>());
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

        setExpectationsForFile(3, newFile, new LinkedList<>());

		replayAll();
		ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(0, result.size());

		copyInFile(THIRD_FILE);
		result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, new LinkedList<>());
		assertEquals(1, result.size());
		verifyAll();	
	}

    @Test
    public void shouldApplyFilesInAFolderWithUpdate() throws CfnAssistException, IOException, InterruptedException {
        List<File> allFiles = loadFiles(FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toFile());

        EasyMock.expect(cloudRepository.getZones()).andReturn(new HashMap<>());
        EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("0");
        // first file - no parameters needed
        setExpectationsForValidationPass(allFiles);

        LinkedList<Parameter> cfnParams = new LinkedList<>();

        List<TemplateParameter> templateParameters = Collections.singletonList(new TemplateParameter().
                withParameterKey(AwsFacade.PARAMETER_STACKNAME).withDefaultValue("01createSubnet"));
        // processing pass
        setExpectationsForFile(1, allFiles.get(0), new LinkedList<>());
        setUpdateExpectations("CfnAssistTest01createSubnet", allFiles.get(1).getAbsolutePath(), templateParameters,
                cfnParams);
        vpcRepository.setVpcIndexTag(projectAndEnv, "2");
        EasyMock.expectLastCall();

        replayAll();
        ArrayList<StackNameAndId> result = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString(),
                projectAndEnv, cfnParams);
        verifyAll();
        assertEquals(2, result.size());
    }

	@Test
	public void shouldRollbackTemplatesWithNoUpdateBasedOnIndex() throws CfnAssistException {
		String stackA = "CfnAssistTest01createSubnet";
        StackEntry stackEntryA = new StackEntry(projectAndEnv.getProject(), projectAndEnv.getEnvTag(),
                new Stack().withStackName(stackA));
		StackNameAndId stackANameAndId = new StackNameAndId(stackA, "id1");
		String stackB = "CfnAssistTest02createAcls";
		StackNameAndId stackBNameAndId = new StackNameAndId(stackB, "id2");
        StackEntry stackEntryB = new StackEntry(projectAndEnv.getProject(), projectAndEnv.getEnvTag(),
                new Stack().withStackName(stackB));

		SetDeltaIndexForProjectAndEnv setDeltaIndexForProjectAndEnv = new SetDeltaIndexForProjectAndEnv(projectAndEnv,vpcRepository);

		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
        EasyMock.expect(cfnRepository.getStacknameByIndex(projectAndEnv.getEnvTag(), 2)).andReturn(stackEntryB);
		EasyMock.expect(cfnRepository.getStackNameAndId(stackB)).andReturn(stackBNameAndId);
		cfnRepository.deleteStack(stackB);
		EasyMock.expectLastCall();
        EasyMock.expect(cfnRepository.getStacknameByIndex(projectAndEnv.getEnvTag(), 1)).andReturn(stackEntryA);
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
		List<String> result = aws.rollbackTemplatesByIndexTag(projectAndEnv);
		verifyAll();
		assertEquals(2, result.size());
		assertTrue(result.contains(stackA));
		assertTrue(result.contains(stackB));
	}

    @Ignore
    @Test
    public void shouldRollbackWithDeltas() throws CfnAssistException {
       fail("no way to do this until we can unpdate tags on a stack");
    }

    @Test
    public void shouldStepBackLastChangeOnAVpc() throws CfnAssistException {
        String stackB = "CfnAssistTest02createAcls";
        StackNameAndId stackBNameAndId = new StackNameAndId(stackB, "id2");

        SetDeltaIndexForProjectAndEnv setDeltaIndexForProjectAndEnv = new SetDeltaIndexForProjectAndEnv(projectAndEnv,vpcRepository);

        EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("2");
        StackEntry stackEntry = new StackEntry(projectAndEnv.getProject(), projectAndEnv.getEnvTag(),
                new Stack().withStackName(stackB));

        EasyMock.expect(cfnRepository.getStacknameByIndex(projectAndEnv.getEnvTag(), 2)).andReturn(stackEntry);
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
        List<String> result = aws.stepbackLastChange(projectAndEnv);
        verifyAll();
        assertEquals(1, result.size());
        assertTrue(result.contains(stackB));
    }

    @Ignore
    @Test
    public void shouldStepBackLastChangeOnAVpcWhenUpdate()  {
        fail("Cannot support this until way to update tag on an existing stack");
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

	private void setExpectationsForFile(Integer count, File file, List<TemplateParameter> templateParameters)
            throws IOException, CfnAssistException, InterruptedException {
		String templateContents = EnvironmentSetupForTests.loadFile(file.getAbsolutePath());

		Collection<Parameter> creationParameters = new LinkedList<>();
		String stackName = aws.createStackName(file, projectAndEnv);
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, count.toString());

		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(Vpc.builder().build());
		EasyMock.expect(cfnRepository.validateStackTemplate(templateContents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");
		// tagging
		Tagging tagging = new Tagging();
        tagging.setIndexTag(count);
        // create stack
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, templateContents, stackName, creationParameters, monitor, tagging))
			.andReturn(stackNameAndId);
		// monitor
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());
		// notification
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, StackStatus.CREATE_COMPLETE.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sentMessageId");
		// success
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(
                new Stack().withStackId(count.toString()).withStackName(stackName));
		// index update
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

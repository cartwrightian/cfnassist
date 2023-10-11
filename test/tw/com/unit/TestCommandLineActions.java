package tw.com.unit;

import org.apache.commons.cli.MissingArgumentException;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackDriftStatus;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import tw.com.*;
import tw.com.commandline.CommandExecutor;
import tw.com.commandline.Main;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

@RunWith(EasyMockRunner.class)
public class TestCommandLineActions extends EasyMockSupport {

	private AwsFacade facade;
	private FacadeFactory factory;
	private ArtifactUploader artifactUploader;
	private DiagramCreator diagramCreator;

	private ProjectAndEnv projectAndEnv;
	private StackNameAndId stackNameAndId;
	private Collection<Parameter> params;
	
	private String comment = "theComment";
	private ProvidesCurrentIp ipProvider;

	@Before
	public void beforeEachTestRuns() {
		
		factory = createMock(FacadeFactory.class);
		facade = createMock(AwsFacade.class);
		artifactUploader = createMock(ArtifactUploader.class);
		diagramCreator = createMock(DiagramCreator.class);
		ipProvider = createStrictMock(ProvidesCurrentIp.class);
		
		params = new LinkedList<>();
		stackNameAndId = new StackNameAndId("someName", "someId");
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

		projectAndEnv.setComment(comment);
	}
	
	@Test
	public void shouldInitVPCWithEnvironmentAndProject() throws MissingArgumentException, CfnAssistException, InterruptedException {
		String[] args = CLIArgBuilder.initVPC(EnvironmentSetupForTests.ENV, EnvironmentSetupForTests.PROJECT, "vpcID");
		setVPCopExpectations();		
		facade.initEnvAndProjectForVPC("vpcID", projectAndEnv);		
		validate(args);	
	}

	@Test
	public void shouldSetTagOnVPC() throws InterruptedException, MissingArgumentException, CfnAssistException {
		String[] args = CLIArgBuilder.tagVPC("TEST_TAG_NAME", "TestTagValue");
		setVPCopExpectations();
		facade.setTagForVpc(projectAndEnv, "TEST_TAG_NAME", "TestTagValue");
		validate(args);
	}
	
	@Test
	public void shouldResetVPCIndexEnvironmentAndProject() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-reset"
				};
		
		setVPCopExpectations();		
		facade.resetDeltaIndex(projectAndEnv);		
		validate(args);	
	}

	// TODO stop extra parameters on this call?
	@Test
	public void shouldResetVPCIndexEnvironmentAndProjectWithParams() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-reset",
				"-parameters", "testA=123;testB=123"
				};
		
		setVPCopExpectations();		
		facade.resetDeltaIndex(projectAndEnv);		
		validate(args);	
	}
	
	@Test
	public void shouldCreateStackNoIAMCapabailies() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		File file = new File(FilesForTesting.SIMPLE_STACK);
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSimpleStack(comment));
	}
	
	@Test
	public void shouldCreateStackWithIAMCapabailies() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		File file = new File(FilesForTesting.STACK_IAM_CAP);
		projectAndEnv.setUseCapabilityIAM();
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createIAMStack(comment));
	}
	
	@Test
	public void shouldUpdateStack() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		File file = new File(FilesForTesting.SUBNET_STACK_DELTA);
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.updateSimpleStack(comment, ""));
	}
	
	@Test
	public void shouldUpdateStackSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		factory.setSNSMonitoring();
		File file = new File(FilesForTesting.SUBNET_STACK_DELTA);		
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.updateSimpleStack(comment, "-sns"));
	}
	
	@Test
	public void shouldCreateStackWithParams() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();

		File file = new File(FilesForTesting.SUBNET_WITH_PARAM);	
		params.add(createParameter("zoneA", "eu-west-1a"));
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSubnetStackWithParams(comment));
	}
	
	@Test
	public void shouldCreateStacksFromDir() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();

		ArrayList<StackNameAndId> stacks = new ArrayList<>();
		EasyMock.expect(facade.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, params)).andReturn(stacks);
			
		validate(CLIArgBuilder.deployFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "", comment));
	}
	
	@Test
	public void shouldCreateStacksFromDirWithSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		factory.setSNSMonitoring();

		ArrayList<StackNameAndId> stacks = new ArrayList<>();
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, params)).andReturn(stacks);
			
		validate(CLIArgBuilder.deployFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns", comment));
	}

	@Test
	public void testShouldListInstances() throws MissingArgumentException, CfnAssistException, InterruptedException {
		SearchCriteria criteria = new SearchCriteria(projectAndEnv);

		setFactoryExpectations();
		
		List<InstanceSummary> summary = new LinkedList<>();
		EasyMock.expect(facade.listInstances(criteria)).andReturn(summary);
		
		validate(CLIArgBuilder.listInstances());
	}

	@Test
	public void shouldUpdateELB() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		Integer buildNumber = 42;
		String typeTag = "web";
		
		projectAndEnv.addBuildNumber(buildNumber);
		facade.updateELBToInstancesMatchingBuild(projectAndEnv, typeTag);
		EasyMock.expectLastCall();
			
		validate(CLIArgBuilder.updateELB(typeTag, buildNumber));
	}

	@Test
	public void shouldUpdateTargetGroup() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		Integer buildNumber = 42;
		String typeTag = "web";
		int port = 4242;

		projectAndEnv.addBuildNumber(buildNumber);
		facade.updateTargetGroupToInstancesMatchingBuild(projectAndEnv, typeTag, port);
		EasyMock.expectLastCall();

		validate(CLIArgBuilder.updateTargetGroup(typeTag, buildNumber, port));
	}
	
	@Test
	public void shouldTidyOldInstances() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		
		File file = new File(FilesForTesting.SIMPLE_STACK);		
		facade.tidyNonLBAssocStacks(file, projectAndEnv, "typeTag");
				
		validate(CLIArgBuilder.tidyNonLBAssociatedStacks());
	}

	@Test
	public void shouldCreateStackWithSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		factory.setSNSMonitoring();

		File file = new File(FilesForTesting.SIMPLE_STACK);		
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSimpleStackWithSNS(comment));
	}
	
	@Test
	public void shouldListStacks() throws MissingArgumentException, CfnAssistException, InterruptedException {
		String stackName = "theStackName";
		String project = "theProject";
		String stackId = "theStackId";
		String env = "theEnv";
		
		setFactoryExpectations();
			
		List<StackEntry> stackEntries = new LinkedList<>();
		Stack stack = Stack.builder().stackName(stackName).stackId(stackId).stackStatus(StackStatus.CREATE_COMPLETE).build();
		stackEntries.add(new StackEntry(project, new EnvironmentTag(env), stack));
		EasyMock.expect(facade.listStacks(projectAndEnv)).andReturn(stackEntries);
		
		String output = validate(CLIArgBuilder.listStacks());

		CLIArgBuilder.checkForExpectedLine(output, stackName, project, env, StackStatus.CREATE_COMPLETE.toString());
	}

	@Test
	public void shouldListDriftStatusForStacks() throws InterruptedException, MissingArgumentException, CfnAssistException {
		String stackName = "theStackName";
		String project = "theProject";
		String stackId = "theStackId";
		String env = "theEnv";

		List<StackEntry> stackEntries = new LinkedList<>();
		Stack stack = Stack.builder().stackName(stackName).stackId(stackId).stackStatus(StackStatus.CREATE_COMPLETE).build();
		StackEntry stackEntry = new StackEntry(project, new EnvironmentTag(env), stack);
		stackEntry.setDriftStatus(new CFNClient.DriftStatus(stackName, StackDriftStatus.IN_SYNC,0));
		stackEntries.add(stackEntry);

		EasyMock.expect(facade.listStackDrift(projectAndEnv)).andReturn(stackEntries);

		setFactoryExpectations();

		String output = validate(CLIArgBuilder.listStackDrift());

		CLIArgBuilder.checkForExpectedLine(output, stackName, project, env, StackDriftStatus.IN_SYNC.toString(), "0");
	}

	@Test
	public void shouldCreateStackWithBuildNumber() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		File file = new File(FilesForTesting.SIMPLE_STACK);
		projectAndEnv.addBuildNumber(915);
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSimpleStackWithBuildNumber(comment, "0915"));
	}
	
	@Test
	public void shouldDeleteByFileStack() throws MissingArgumentException, CfnAssistException, InterruptedException
	{
		setFactoryExpectations();
		File file = new File(FilesForTesting.SIMPLE_STACK);	
		facade.deleteStackFrom(file, projectAndEnv);
			
		validate(CLIArgBuilder.deleteSimpleStack());
	}
	
	@Test
	public void shouldDeleteByFileStackWithBuildNumber() throws MissingArgumentException, CfnAssistException, InterruptedException
	{
		setFactoryExpectations();
		File file = new File(FilesForTesting.SIMPLE_STACK);	
		projectAndEnv.addBuildNumber(915);
		facade.deleteStackFrom(file, projectAndEnv);
			
		validate(CLIArgBuilder.deleteSimpleStackWithBuildNumber("0915"));
	}

    @Test
    public void shouldDeleteByNameStack() throws MissingArgumentException, CfnAssistException, InterruptedException
    {
        setFactoryExpectations();
        facade.deleteStackByName("simpleStack", projectAndEnv);

        validate(CLIArgBuilder.deleteByNameSimpleStack("simpleStack"));
    }

    @Test
    public void shouldDeleteByNameStackWithBuildNumber() throws MissingArgumentException, CfnAssistException, InterruptedException
    {
        setFactoryExpectations();
        projectAndEnv.addBuildNumber(915);
        facade.deleteStackByName("simpleStack", projectAndEnv);

        validate(CLIArgBuilder.deleteByNameSimpleStackWithBuildNumber("simpleStack", "0915"));
    }
	
	@Test
	public void testUploadArtifactsAndInvokeStackCreate() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		Integer buildNumber = 9987;
		// src files to upload
		Collection<Parameter> arts = new LinkedList<>();
		arts.add(createParameter("urlA", FilesForTesting.ACL));
		arts.add(createParameter("urlB", FilesForTesting.SUBNET_STACK_JSON));
		// locations after upload
		Parameter uploadA = createParameter("urlA", "fileAUploadLocation");
		Parameter uploadB = createParameter("urlB", "fileBUploadLocation");
		List<Parameter> uploaded = new LinkedList<>();
		uploaded.add(uploadA);
		uploaded.add(uploadB);
		
		setFactoryExpectations();
		factory.setSNSMonitoring();

		projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(factory.createArtifactUploader(projectAndEnv)).andReturn(artifactUploader);
		EasyMock.expect(artifactUploader.uploadArtifacts(arts)).andReturn(uploaded);
		params.add(uploadA);
		params.add(uploadB);
		
		projectAndEnv.setUseSNS();
		File file = new File(FilesForTesting.SUBNET_WITH_S3_PARAM);
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
		
		validate(CLIArgBuilder.createSubnetStackWithArtifactUpload(buildNumber, comment));
	}

	private Parameter createParameter(String key, String value) {
		return Parameter.builder().parameterKey(key).parameterValue(value).build();
	}

	@Test
	public void shouldUploadArtifacts() {
		Integer buildNumber = 9987;
		Collection<Parameter> arts = new LinkedList<>();
		arts.add(createParameter("art1",FilesForTesting.ACL));
		arts.add(createParameter("art2",FilesForTesting.SUBNET_STACK_JSON));
		List<Parameter> uploaded = new LinkedList<>();
		
		factory.setProject(EnvironmentSetupForTests.PROJECT);
		
		projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(factory.createArtifactUploader(projectAndEnv)).andReturn(artifactUploader);
		EasyMock.expect(artifactUploader.uploadArtifacts(arts)).andReturn(uploaded);
		
		validate(CLIArgBuilder.uploadArtifacts(buildNumber));
	}
	
	@Test
	public void shouldDeleteArtifacts() {
		Integer buildNumber = 9987;
		String filenameA = "fileA";
		String filenameB = "fileB";

		factory.setProject(EnvironmentSetupForTests.PROJECT);
        EasyMock.expectLastCall();

        projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(factory.createArtifactUploader(projectAndEnv)).andReturn(artifactUploader);
		artifactUploader.delete(filenameA);
        EasyMock.expectLastCall();
        artifactUploader.delete(filenameB);
        EasyMock.expectLastCall();

        validate(CLIArgBuilder.deleteArtifacts(buildNumber, filenameA, filenameB));
	}
	
	@Test
	public void shouldRequestCreationOfDiagrams() throws CfnAssistException, IOException {
		Recorder recorder = new FileRecorder(Paths.get("./diagrams"));
		EasyMock.expect(factory.createDiagramCreator()).andReturn(diagramCreator);
		diagramCreator.createDiagrams(recorder);
		
		String folder = "./diagrams";
		validate(CLIArgBuilder.createDiagrams(folder));
	}
	
	@Test
	public void testShouldAllowCurrentIpOnELB() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		String type = "elbTypeTag";
		Integer port = 8080;

		EasyMock.expect(factory.getCurrentIpProvider()).andReturn(ipProvider);
		facade.addCurrentIPWithPortToELB(projectAndEnv, type, ipProvider, port);
		EasyMock.expectLastCall();
		
		validate(CLIArgBuilder.allowlistCurrentIP(type, port));
	}

    @Test
    public void testShouldBlockCurrentIpOnELB() throws MissingArgumentException, CfnAssistException, InterruptedException {
        setFactoryExpectations();
        String type = "elbTypeTag";
        Integer port = 8080;

        EasyMock.expect(factory.getCurrentIpProvider()).andReturn(ipProvider);
        facade.removeCurrentIPAndPortFromELB(projectAndEnv, type, ipProvider, port);
        EasyMock.expectLastCall();

        validate(CLIArgBuilder.blockCurrentIP(type, port));
    }

    @Test
    public void testShouldAllowHostOnELB() throws MissingArgumentException, CfnAssistException, InterruptedException, UnknownHostException {
        setFactoryExpectations();
        String type = "elbTypeTag";
        Integer port = 8080;

        String hostname = "www.somehost.com";

        facade.addHostAndPortToELB(projectAndEnv, type, hostname, port);
        EasyMock.expectLastCall();

        validate(CLIArgBuilder.allowHost(type, hostname, port));
    }

    @Test
    public void shouldTidyOldCloudwatchLogs() throws InterruptedException, MissingArgumentException, CfnAssistException {
	    setFactoryExpectations();

	    int days = 4;

	    facade.removeCloudWatchLogsOlderThan(projectAndEnv, days);
	    EasyMock.expectLastCall();

	    validate(CLIArgBuilder.tidyCloudWatch(days));
    }

    @Test
    public void shouldTagCloudwatchLog() throws InterruptedException, MissingArgumentException, CfnAssistException {
	    setFactoryExpectations();

	    facade.tagCloudWatchLog(projectAndEnv, "logGroupName");
	    EasyMock.expectLastCall();

	    validate(CLIArgBuilder.tagCloudWatchLog("logGroupName"));
    }

    @Test
    public void shouldGetLogs() throws InterruptedException, MissingArgumentException, CfnAssistException {
	    setFactoryExpectations();
        Integer hours = 42;
        EasyMock.expect(facade.fetchLogs(projectAndEnv, hours)).andReturn(new LinkedList<>());

	    validate(CLIArgBuilder.getLogs(hours));
    }

    @Test
    public void testShouldBlockHostOnELB() throws MissingArgumentException, CfnAssistException, InterruptedException, UnknownHostException {
        setFactoryExpectations();
        String type = "elbTypeTag";
        Integer port = 8080;

        String hostname = "www.somehost.com";

        facade.removeHostAndPortFromELB(projectAndEnv, type, hostname, port);
        EasyMock.expectLastCall();

        validate(CLIArgBuilder.blockHost(type, hostname, port));
    }

	@Test
    public void shouldCreateKeypairWithNoFilename() throws InterruptedException, MissingArgumentException, CfnAssistException {
        String home = System.getenv("HOME");
        Path filename = Paths.get(format("%s/.ssh/CfnAssist_Test.pem",home));

        //KeyPair keyPair = new KeyPair().withKeyFingerprint("fingerprint").withKeyName("keyName");
		CloudClient.AWSPrivateKey keyPair = new CloudClient.AWSPrivateKey("keyName", "material");

        SavesFile savesFile = EasyMock.createMock(SavesFile.class);

        setFactoryExpectations();

        EasyMock.expect(factory.getSavesFile()).andReturn(savesFile);
		EasyMock.expect(facade.createKeyPair(projectAndEnv, savesFile, filename)).andReturn(keyPair);

        validate((CLIArgBuilder.createKeyPair("")));
    }

    @Test
    public void shouldCreateKeypairWithFilename() throws InterruptedException, MissingArgumentException, CfnAssistException {
        Path filename = Paths.get("someFilename");
        SavesFile savesFile = EasyMock.createMock(SavesFile.class);
		CloudClient.AWSPrivateKey keyPair = new CloudClient.AWSPrivateKey("keyName", "material");

        setFactoryExpectations();

        EasyMock.expect(factory.getSavesFile()).andReturn(savesFile);
        EasyMock.expect(facade.createKeyPair(projectAndEnv, savesFile, filename)).andReturn(keyPair);

        validate(CLIArgBuilder.createKeyPair(filename.toString()));
    }

	@Test
    public void shouldExecuteSSHCommandDefaultUser() throws InterruptedException, MissingArgumentException, CfnAssistException, IOException {
        CommandExecutor commandExecutor = createMock(CommandExecutor.class);

        setFactoryExpectations();
        List<String> commandList = new LinkedList<>();
        commandList.add("theCommandText");
        EasyMock.expect(facade.createSSHCommand(projectAndEnv, "ec2-user")).andReturn(commandList);
        EasyMock.expect(factory.getCommandExecutor()).andReturn(commandExecutor);

        commandExecutor.execute(commandList);
        EasyMock.expectLastCall();

        validate(CLIArgBuilder.createSSHCommand(""));
    }

    @Test
    public void shouldExecuteSSHCommandProvidedUser() throws InterruptedException, MissingArgumentException, CfnAssistException, IOException {
        CommandExecutor commandExecutor = createMock(CommandExecutor.class);

        setFactoryExpectations();
        List<String> commandList = new LinkedList<>();
        commandList.add("theCommandText");
        EasyMock.expect(facade.createSSHCommand(projectAndEnv, "userName")).andReturn(commandList);
        EasyMock.expect(factory.getCommandExecutor()).andReturn(commandExecutor);

        commandExecutor.execute(commandList);
        EasyMock.expectLastCall();

        validate(CLIArgBuilder.createSSHCommand("userName"));
    }

    @Test
    public void shouldPurgeAllStacks() throws MissingArgumentException, CfnAssistException, InterruptedException {
        setFactoryExpectations();

        ArrayList<String> stacks = new ArrayList<>();
        EasyMock.expect(facade.rollbackTemplatesByIndexTag(projectAndEnv)).andReturn(stacks);

        validate(CLIArgBuilder.purge(""));
    }

    @Test
    public void testShouldStepBackOneChange() throws MissingArgumentException, CfnAssistException, InterruptedException {
        setFactoryExpectations();

        List<String> deleted = new LinkedList<>();
        EasyMock.expect(facade.stepbackLastChange(projectAndEnv)).andReturn(deleted);

        validate(CLIArgBuilder.back(""));
    }

    @Test
    public void shouldPurgeStacks() throws MissingArgumentException, CfnAssistException, InterruptedException {
        setFactoryExpectations();
        factory.setSNSMonitoring();

        ArrayList<String> stacks = new ArrayList<>();
        projectAndEnv.setUseSNS();
        EasyMock.expect(facade.rollbackTemplatesByIndexTag(projectAndEnv)).andReturn(stacks);

        validate(CLIArgBuilder.purge("-sns"));
    }

    @Test
	public void shouldNotAllowSNSWithS3Create() {
		String artifacts = format("art1=%s;art2=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK_JSON);
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-s3create",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", "0042",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void shouldNotAllowSNSWithS3Delete() {
		String artifacts = format("art1=%s;art2=%s", "fileA", "fileB");
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-s3delete",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", "0042",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void shouldNotAllowBuildNumberWithStackTidy() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-tidyOldStakcs", FilesForTesting.SIMPLE_STACK,
				"-build", "3373"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void shouldNotAllowBuildParameterWithDirAction() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", FilesForTesting.ORDERED_SCRIPTS_FOLDER,
				"-build", "001"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testInvokeInitViaCommandLineMissingValue() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-init"
				};
		expectCommandLineFailureStatus(args);
	}
		
	@Test
	public void testCommandLineWithExtraIncorrectParams() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-reset",
				"-parameters", "testA=123;testB"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testMustGiveTheBuildNumberWhenUploadingArtifacts() {
		String uploads = format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK_JSON);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SUBNET_WITH_PARAM,
				"-artifacts", uploads,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testMustGiveTheBucketWhenUploadingArtifacts() {
		String uploads = format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK_JSON);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SUBNET_WITH_PARAM,
				"-artifacts", uploads,
				"-build", "9987",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testMustGiveFileAndTypeTagWhenInvokingStackTidyCommand() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-tidyOldStacks", FilesForTesting.SIMPLE_STACK
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testUploadArgumentParsingFailsWithoutBucket() {
		
		String uploads = format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK_JSON);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
				"-artifacts", uploads,
				"-build", "9987",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	private void expectCommandLineFailureStatus(String[] args) {
		Main main = new Main(args);
		int result = main.parse(factory,true);
		assertEquals(EnvironmentSetupForTests.FAILURE_STATUS, result);
	}

	private String validate(String[] args) {
		replayAll();
		Main main = new Main(args);

        PrintStream original = System.out;

        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(arrayOutputStream);
        System.setOut(printStream);
        int result = main.parse(factory, true);
        printStream.close();
        System.setOut(original);

		String output = new String(arrayOutputStream.toByteArray(), Charset.defaultCharset());

		verifyAll();

		assertEquals(output, 0,result);
		return output;
	}

	private void setFactoryExpectations()
			throws MissingArgumentException, CfnAssistException,
			InterruptedException {
		factory.setProject(EnvironmentSetupForTests.PROJECT);
		EasyMock.expect(factory.createFacade()).andReturn(facade);
	}
	
	private void setVPCopExpectations() throws MissingArgumentException,
		CfnAssistException, InterruptedException {
		factory.setProject(EnvironmentSetupForTests.PROJECT);
		EasyMock.expect(factory.createFacade()).andReturn(facade);
	}
}

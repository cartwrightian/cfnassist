package tw.com.unit;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.elasticloadbalancing.model.Instance;

import tw.com.AwsFacade;
import tw.com.CLIArgBuilder;
import tw.com.EnvironmentSetupForTests;
import tw.com.FacadeFactory;
import tw.com.FilesForTesting;
import tw.com.commandline.Main;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.InstanceSummary;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.ArtifactUploader;
import tw.com.providers.ProvidesCurrentIp;

@RunWith(EasyMockRunner.class)
public class TestCommandLineActions extends EasyMockSupport {

	private AwsFacade facade;
	private FacadeFactory facadeFactory;
	private ArtifactUploader artifactUploader;
	private DiagramCreator diagramCreator;

	private ProjectAndEnv projectAndEnv;
	private StackNameAndId stackNameAndId;
	Collection<Parameter> params;
	
	String comment = "theComment";
	private ProvidesCurrentIp ipProvider;

	@Before
	public void beforeEachTestRuns() {
		
		facadeFactory = createMock(FacadeFactory.class);
		facade = createMock(AwsFacade.class);
		artifactUploader = createMock(ArtifactUploader.class);
		diagramCreator = createMock(DiagramCreator.class);
		ipProvider = createStrictMock(ProvidesCurrentIp.class);
		
		params = new LinkedList<Parameter>();
		stackNameAndId = new StackNameAndId("someName", "someId");
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	}
	
	@Test
	public void shouldInitVPCWithEnvironmentAndProject() throws MissingArgumentException, CfnAssistException, InterruptedException {
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init", "vpcID"
				};
		
		setVPCopExpectations();		
		facade.initEnvAndProjectForVPC("vpcID", projectAndEnv);		
		validate(args);	
	}
	
	@Test
	public void shouldResetVPCIndexEnvironmentAndProject() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-reset", 
				"-parameters", "testA=123;testB=123"
				};
		
		setVPCopExpectations();		
		facade.resetDeltaIndex(projectAndEnv);		
		validate(args);	
	}
	
	@Test
	public void shouldCreateStackNoIAMCapabailies() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException {		
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);
		File file = new File(FilesForTesting.SIMPLE_STACK);		
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSimpleStack(comment));
	}
	
	@Test
	public void shouldCreateStackWithIAMCapabailies() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException {		
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);
		File file = new File(FilesForTesting.STACK_IAM_CAP);	
		projectAndEnv.setUseCapabilityIAM();
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createIAMStack(comment));
	}
	
	@Test
	public void shouldUpdateStack() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);
		File file = new File(FilesForTesting.SUBNET_STACK_DELTA);		
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.updateSimpleStack(comment,""));
	}
	
	@Test
	public void shouldUpdateStackSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);
		facadeFactory.setSNSMonitoring();
		File file = new File(FilesForTesting.SUBNET_STACK_DELTA);		
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.updateSimpleStack(comment,"-sns"));
	}
	
	@Test
	public void shouldCreateStackWithParams() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);

		File file = new File(FilesForTesting.SUBNET_WITH_PARAM);	
		params.add(new Parameter().withParameterKey("zoneA").withParameterValue("eu-west-1a"));
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSubnetStackWithParams(comment));
	}
	
	@Test
	public void shouldCreateStacksFromDir() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);

		ArrayList<StackNameAndId> stacks = new ArrayList<StackNameAndId>();
		EasyMock.expect(facade.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, params)).andReturn(stacks);
			
		validate(CLIArgBuilder.deployFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "", comment));
	}
	
	@Test
	public void shouldCreateStacksFromDirWithSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setSNSMonitoring();
		facadeFactory.setCommentTag(comment);

		ArrayList<StackNameAndId> stacks = new ArrayList<StackNameAndId>();
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv, params)).andReturn(stacks);
			
		validate(CLIArgBuilder.deployFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns", comment));
	}
	
	@Test
	public void shouldRollbackStacksFromDir() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();

		ArrayList<String> stacks = new ArrayList<String>();
		EasyMock.expect(facade.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv)).andReturn(stacks);
			
		validate(CLIArgBuilder.rollbackFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, ""));
	}
	
	@Test
	public void testShouldStepBackOneChange() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		
		List<String> deleted = new LinkedList<>();
		EasyMock.expect(facade.stepbackLastChange(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv)).andReturn(deleted);
		
		validate(CLIArgBuilder.stepback(FilesForTesting.ORDERED_SCRIPTS_FOLDER, ""));
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
	public void shouldRollbackStacksFromDirWithSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setSNSMonitoring();

		ArrayList<String> stacks = new ArrayList<String>();
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv)).andReturn(stacks);
			
		validate(CLIArgBuilder.rollbackFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns"));
	}
	
	@Test
	public void shouldUpdateELB() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		String buildNumber = "0042";
		String typeTag = "web";
		
		List<Instance> instances = new LinkedList<Instance>();
		projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(facade.updateELBToInstancesMatchingBuild(projectAndEnv, typeTag)).andReturn(instances);
			
		validate(CLIArgBuilder.updateELB(typeTag, buildNumber));
	}
	
	@Test
	public void shouldTidyOldInstances() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		
		File file = new File(FilesForTesting.SIMPLE_STACK);		
		facade.tidyNonLBAssocStacks(file, projectAndEnv, "typeTag");
				
		validate(CLIArgBuilder.tidyNonLBAssociatedStacks());
	}
	
	
	@Test
	public void shouldCreateStackWithSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		setFactoryExpectations();
		facadeFactory.setSNSMonitoring();
		facadeFactory.setCommentTag(comment);

		File file = new File(FilesForTesting.SIMPLE_STACK);		
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSimpleStackWithSNS(comment));
	}
	
	@Test
	public void shouldListStacks() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		PrintStream origStream = System.out;
		String stackName = "theStackName";
		String project = "theProject";
		String stackId = "theStackId";
		String env = "theEnv";
		
		setFactoryExpectations();
			
		List<StackEntry> stackEntries = new LinkedList<StackEntry>();
		Stack stack = new Stack().withStackName(stackName).withStackId(stackId).withStackStatus(StackStatus.CREATE_COMPLETE);
		stackEntries.add(new StackEntry(project, new EnvironmentTag(env), stack));
		EasyMock.expect(facade.listStacks(projectAndEnv)).andReturn(stackEntries);
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintStream output = new PrintStream(stream);
		System.setOut(output);
		
		validate(CLIArgBuilder.listStacks());
		
		System.setOut(origStream);
		
		CLIArgBuilder.checkForExpectedLine(stackName, project, env, stream);
	}

	@Test
	public void shouldCreateStackWithBuildNumber() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {
		setFactoryExpectations();
		facadeFactory.setCommentTag(comment);
		File file = new File(FilesForTesting.SIMPLE_STACK);	
		projectAndEnv.addBuildNumber("0915");
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);
			
		validate(CLIArgBuilder.createSimpleStackWithBuildNumber(comment, "0915"));
	}
	
	@Test
	public void shouldDeleteStack() throws MissingArgumentException, CfnAssistException, InterruptedException
	{
		setFactoryExpectations();
		File file = new File(FilesForTesting.SIMPLE_STACK);	
		facade.deleteStackFrom(file, projectAndEnv);
			
		validate(CLIArgBuilder.deleteSimpleStack());
	}
	
	@Test
	public void shouldDeleteStackWithBuildNumber() throws MissingArgumentException, CfnAssistException, InterruptedException
	{
		setFactoryExpectations();
		File file = new File(FilesForTesting.SIMPLE_STACK);	
		projectAndEnv.addBuildNumber("0915");
		facade.deleteStackFrom(file, projectAndEnv);
			
		validate(CLIArgBuilder.deleteSimpleStackWithBuildNumber("0915"));
	}
	
	@Test
	public void testUploadArtifactsAndInvokeStackCreate() throws MissingArgumentException, CfnAssistException, InterruptedException, FileNotFoundException, IOException, InvalidStackParameterException {		
		String buildNumber = "9987";
		// src files to upload
		Collection<Parameter> arts = new LinkedList<Parameter>();
		arts.add(new Parameter().withParameterKey("urlA").withParameterValue(FilesForTesting.ACL));
		arts.add(new Parameter().withParameterKey("urlB").withParameterValue(FilesForTesting.SUBNET_STACK));
		// locations after upload
		Parameter uploadA = new Parameter().withParameterKey("urlA").withParameterValue("fileAUploadLocation");
		Parameter uploadB = new Parameter().withParameterKey("urlB").withParameterValue("fileBUploadLocation");
		List<Parameter> uploaded = new LinkedList<Parameter>();
		uploaded.add(uploadA);
		uploaded.add(uploadB);
		
		setFactoryExpectations();
		facadeFactory.setSNSMonitoring();
		facadeFactory.setCommentTag(comment);

		projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(facadeFactory.createArtifactUploader(projectAndEnv)).andReturn(artifactUploader);
		EasyMock.expect(artifactUploader.uploadArtifacts(arts)).andReturn(uploaded);
		params.add(uploadA);
		params.add(uploadB);
		
		projectAndEnv.setUseSNS();
		File file = new File(FilesForTesting.SUBNET_WITH_S3_PARAM);
		EasyMock.expect(facade.applyTemplate(file, projectAndEnv, params)).andReturn(stackNameAndId);;
		
		validate(CLIArgBuilder.createSubnetStackWithArtifactUpload(buildNumber, comment));
	}
	
	@Test
	public void shouldUploadArtifacts() throws MissingArgumentException, CfnAssistException, InterruptedException {
		String buildNumber = "9987";
		Collection<Parameter> arts = new LinkedList<Parameter>();
		arts.add(new Parameter().withParameterKey("art1").withParameterValue(FilesForTesting.ACL));
		arts.add(new Parameter().withParameterKey("art2").withParameterValue(FilesForTesting.SUBNET_STACK));
		List<Parameter> uploaded = new LinkedList<Parameter>();
		
		facadeFactory.setRegion(EnvironmentSetupForTests.getRegion());
		facadeFactory.setProject(EnvironmentSetupForTests.PROJECT);
		
		projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(facadeFactory.createArtifactUploader(projectAndEnv)).andReturn(artifactUploader);
		EasyMock.expect(artifactUploader.uploadArtifacts(arts)).andReturn(uploaded);
		
		validate(CLIArgBuilder.uploadArtifacts(buildNumber));
	}
	
	@Test
	public void shouldDeleteArtifacts() throws MissingArgumentException, CfnAssistException, InterruptedException {
		String buildNumber = "9987";
		Collection<Parameter> arts = new LinkedList<Parameter>();
		String filenameA = "fileA";
		arts.add(new Parameter().withParameterKey("art1").withParameterValue(filenameA));
		String filenameB = "fileB";
		arts.add(new Parameter().withParameterKey("art2").withParameterValue(filenameB));
		
		facadeFactory.setRegion(EnvironmentSetupForTests.getRegion());
		facadeFactory.setProject(EnvironmentSetupForTests.PROJECT);
		
		projectAndEnv.addBuildNumber(buildNumber);
		EasyMock.expect(facadeFactory.createArtifactUploader(projectAndEnv)).andReturn(artifactUploader);
		artifactUploader.delete(filenameA);
		artifactUploader.delete(filenameB);
		
		validate(CLIArgBuilder.deleteArtifacts(buildNumber, filenameA, filenameB));
	}
	
	@Test
	public void shouldRequestCreationOfDiagrams() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {			
		Recorder recorder = new FileRecorder(Paths.get("./diagrams"));
		EasyMock.expect(facadeFactory.createDiagramCreator()).andReturn(diagramCreator);
		facadeFactory.setRegion(EnvironmentSetupForTests.getRegion());
		diagramCreator.createDiagrams(recorder);
		
		String folder = "./diagrams";
		validate(CLIArgBuilder.createDiagrams(folder));
	}
	
	@Test
	public void testShouldWhiteListCurrentIpOnELB() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		String type = "elbTypeTag";
		Integer port = 8080;

		EasyMock.expect(facadeFactory.getCurrentIpProvider()).andReturn(ipProvider);
		facade.whitelistCurrentIpForPortToElb(projectAndEnv, type, ipProvider, port);
		EasyMock.expectLastCall();
		
		validate(CLIArgBuilder.whitelistCurrentIP(type, port));
	}
	
	@Test
	public void testShouldBlackListCurrentIpOnELB() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		String type = "elbTypeTag";
		Integer port = 8080;

		EasyMock.expect(facadeFactory.getCurrentIpProvider()).andReturn(ipProvider);
		facade.blacklistCurrentIpForPortToElb(projectAndEnv, type, ipProvider, port);
		EasyMock.expectLastCall();
		
		validate(CLIArgBuilder.blacklistCurrentIP(type, port));
	}

	
	@Test
	public void shouldNotAllowSNSWithS3Create() {
		String artifacts = String.format("art1=%s;art2=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
		String artifacts = String.format("art1=%s;art2=%s", "fileA", "fileB");
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init" 
				};
		expectCommandLineFailureStatus(args);
	}
		
	@Test
	public void testCommandLineWithExtraIncorrectParams() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-reset",
				"-parameters", "testA=123;testB"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testMustGiveTheBuildNumberWhenUploadingArtifacts() {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
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
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-tidyOldStacks", FilesForTesting.SIMPLE_STACK
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testUploadArgumentParsingFailsWithoutBucket() {
		
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
				"-artifacts", uploads,
				"-build", "9987",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	private void expectCommandLineFailureStatus(String[] args) {
		Main main = new Main(args);
		int result = main.parse(facadeFactory,true);
		assertEquals(EnvironmentSetupForTests.FAILURE_STATUS, result);
	}
	

	private void validate(String[] args) {
		replayAll();
		Main main = new Main(args);
		int result = main.parse(facadeFactory, true);
		verifyAll();
		assertEquals(0,result);
	}

	private void setFactoryExpectations()
			throws MissingArgumentException, CfnAssistException,
			InterruptedException {
		facadeFactory.setRegion(EnvironmentSetupForTests.getRegion());
		facadeFactory.setProject(EnvironmentSetupForTests.PROJECT);
		EasyMock.expect(facadeFactory.createFacade()).andReturn(facade);
	}
	
	private void setVPCopExpectations() throws MissingArgumentException,
		CfnAssistException, InterruptedException {
		facadeFactory.setRegion(EnvironmentSetupForTests.getRegion());
		facadeFactory.setProject(EnvironmentSetupForTests.PROJECT);
		EasyMock.expect(facadeFactory.createFacade()).andReturn(facade);
	}
}

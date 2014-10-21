package tw.com.unit;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.BadVPCDeltaIndexException;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.VpcRepository;

@RunWith(EasyMockRunner.class)
public class TestAwsFacade extends EasyMockSupport {

	private static final String VPC_ID = "vpcId";
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private MonitorStackEvents monitor;

	@Before
	public void beforeEachTestRuns() {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository);
	}
	
	@Test
	public void testShouldInitTagsOnVpc() throws CfnAssistException {
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(null);
		vpcRepository.initAllTags("targetVpc", projectAndEnv);
		EasyMock.expectLastCall();
		
		replayAll();
		aws.initEnvAndProjectForVPC("targetVpc", projectAndEnv);
		verifyAll();
	}
	
	@Test
	public void testShouldInitTagsOnVpcThrowIfAlreadyExists() throws CfnAssistException {
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId("existingId"));
		
		replayAll();
		try {
			aws.initEnvAndProjectForVPC("targetVpc", projectAndEnv);
			fail("expected exception");
		}
		catch(TagsAlreadyInit expected) {
			// expected
		}
		verifyAll();
	}
	
	@Test
	public void shouldGetDeltaIndex() throws CfnAssistException {
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("42");
		
		replayAll();
		int result = aws.getDeltaIndex(projectAndEnv);
		assertEquals(42, result);
		verifyAll();
	}
	
	@Test
	public void shouldGetDeltaIndexThrowsOnNonNumeric() throws CfnAssistException {
		EasyMock.expect(vpcRepository.getVpcIndexTag(projectAndEnv)).andReturn("NaN");
		
		replayAll();
		try {
			aws.getDeltaIndex(projectAndEnv);
			fail("expected exception");
		}
		catch(BadVPCDeltaIndexException expectedException) {
			// no op
		}
		verifyAll();
	}
	
	@Test
	public void shouldSetAndResetDeltaIndex() throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projectAndEnv,"99");
		EasyMock.expectLastCall();
		vpcRepository.setVpcIndexTag(projectAndEnv,"0");
		EasyMock.expectLastCall();
		
		replayAll();
		aws.setDeltaIndex(projectAndEnv, 99);
		aws.resetDeltaIndex(projectAndEnv);
		verifyAll();	
	}
	
	@Test
	public void shouldThrowForUnknownProjectAndEnvCombinationOnDeltaSet() throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projectAndEnv,"99");
		EasyMock.expectLastCall().andThrow(new CannotFindVpcException(projectAndEnv));
		
		replayAll();
		try {
			aws.setDeltaIndex(projectAndEnv, 99);
			fail("Should have thrown exception");
		}
		catch(CannotFindVpcException expected) {
			// expected
		}
		verifyAll();
	}
	
	@Test 
	public void testShouldListStacksEnvSupplied() {	
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		stacks.add(new StackEntry("proj", projectAndEnv.getEnvTag(), new Stack()));
		EasyMock.expect(cfnRepository.getStacks(projectAndEnv.getEnvTag())).andReturn(stacks);
		
		replayAll();
		List<StackEntry> results = aws.listStacks(projectAndEnv);
		assertEquals(1,results.size());
		verifyAll();
	}
	
	@Test 
	public void testShouldListStacksNoEnvSupplied() {	
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		stacks.add(new StackEntry("proj", projectAndEnv.getEnvTag(), new Stack()));
		EasyMock.expect(cfnRepository.getStacks()).andReturn(stacks);
		
		replayAll();
		ProjectAndEnv pattern = new ProjectAndEnv("someProject", "");
		List<StackEntry> results = aws.listStacks(pattern);
		assertEquals(1,results.size());
		verifyAll();
	}
	
	@Test
	public void testShouldInvokeValidation() {	
		List<TemplateParameter> params = new LinkedList<TemplateParameter>();
		params.add(new TemplateParameter().withDescription("a parameter"));
		EasyMock.expect(cfnRepository.validateStackTemplate("someContents")).andReturn(params);
		
		replayAll();
		List<TemplateParameter> results = aws.validateTemplate("someContents");
		verifyAll();
		assertEquals(1, results.size());
	}

	@Test
	public void cannotAddEnvParameter() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("env");
	}
	
	@Test
	public void cannotAddvpcParameter() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("vpc");
	}
	
	@Test
	public void cannotAddbuildParameter() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("build");
	}
	
	@Test
	public void createStacknameFromEnvAndFile() {
		String stackName = aws.createStackName(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);
		assertEquals("CfnAssistTestsimpleStack", stackName);
	}
	
	@Test
	public void createStacknameFromEnvAndFileWithDelta() {
		String stackName = aws.createStackName(new File(FilesForTesting.STACK_UPDATE), projectAndEnv);
		assertEquals("CfnAssistTest02createSubnet", stackName);
	}
	
	@Test 
	public void shouldIncludeBuildNumberWhenFormingStackname() {
		projectAndEnv.addBuildNumber("042");
		String stackName = aws.createStackName(new File(FilesForTesting.SIMPLE_STACK),projectAndEnv);
		
		assertEquals("CfnAssist042TestsimpleStack", stackName);	
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParameters() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateOutputParameters() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();	
		Collection<Output> outputs = new LinkedList<Output>();
		outputs.add(new Output().withDescription("::CFN_TAG").withOutputKey("outputKey").withOutputValue("outputValue"));
		outputs.add(new Output().withDescription("something").withOutputKey("ignored").withOutputValue("noThanks"));
		
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, "", outputs);
		vpcRepository.setVpcTag(projectAndEnv, "outputKey", "outputValue");
		EasyMock.expectLastCall();
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	private StackNameAndId SetCreateExpectations(String stackName,
			String contents, List<TemplateParameter> templateParameters,
			Collection<Parameter> creationParameters) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		return SetCreateExpectations(stackName, contents, templateParameters, creationParameters,""); // no comment
	}

	@Test
	public void shouldApplySimpleTemplateNoParametersWithComment() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, "aComment");
		
		replayAll();
		aws.setCommentTag("aComment");
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateInputParameters() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		addParam(creationParameters, "subnet", "subnetValue");
		
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<Parameter>();
		addParam(userParams, "subnet", "subnetValue");
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplyAutoDiscoveryTemplateInputParameters() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		templateParameters.add(new TemplateParameter().withParameterKey("keyName").withDescription("::logicalIdToFind"));
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		addParam(creationParameters, "keyName", "foundPhysicalId");
		
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = new Stack().withStackId("stackId");
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");	
		// search for the logical id, return the found id
		EasyMock.expect(cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "logicalIdToFind")).andReturn("foundPhysicalId");
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, "")).
			andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.updateRepositoryFor(stackNameAndId)).andReturn(stack);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<Parameter>();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplyAutoDiscoveryVPCTagParameters() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		templateParameters.add(new TemplateParameter().withParameterKey("vpcTagKey").withDescription("::CFN_TAG"));
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		addParam(creationParameters, "vpcTagKey", "foundVpcTagValue");
		
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = new Stack().withStackId("stackId");
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");	
		// get the tag from the VPC
		EasyMock.expect(vpcRepository.getVpcTag("vpcTagKey", projectAndEnv)).andReturn("foundVpcTagValue");
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, "")).
			andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.updateRepositoryFor(stackNameAndId)).andReturn(stack);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<Parameter>();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateInputParametersNotPassBuild() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssist0056TestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		addParam(creationParameters, "subnet", "subnetValue");
		
		projectAndEnv.addBuildNumber("0056");
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<Parameter>();
		addParam(userParams, "subnet", "subnetValue");
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInAndUserParams() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		addParam(creationParameters, "subnet", "subnetValue");
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);
			
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters);
		
		
		List<Parameter> userParams = new LinkedList<Parameter>();
		addParam(userParams, "subnet", "subnetValue");
		replayAll();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv,userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInParams() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);
		
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInParamsWithBuild() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, IOException, InvalidParameterException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssist0043TestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
		templateParameters.add(new TemplateParameter().withParameterKey("build"));
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		projectAndEnv.addBuildNumber("0043");
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);
		addParam(creationParameters, "build", "0043");
		
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldDeleteStack() throws WrongNumberOfStacksException, NotReadyException, WrongStackStatus, InterruptedException {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		
		setDeleteExpectations(stackName, stackNameAndId);
		
		replayAll();
		aws.deleteStackFrom(file, projectAndEnv);
		verifyAll();
	}
	
	@Test
	public void shouldDeleteStackWithBuildNumber() throws WrongNumberOfStacksException, NotReadyException, WrongStackStatus, InterruptedException {
		String stackName = "CfnAssist0057TestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		
		projectAndEnv.addBuildNumber("0057");
		setDeleteExpectations(stackName, stackNameAndId);
		
		replayAll();
		aws.deleteStackFrom(file, projectAndEnv);
		verifyAll();
	}
	
	@Test
	public void shouldThrowOnCreateWhenStackExistsAndNotRolledBack() throws IOException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, InvalidParameterException, InterruptedException  {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		String contents = EnvironmentSetupForTests.loadFile(filename);
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.CREATE_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.getStackId(stackName)).andReturn(stackNameAndId);
		
		replayAll();
		try {
			aws.applyTemplate(filename, projectAndEnv);
		}
		catch(DuplicateStackException expected) {
			// expected 
		}

		verifyAll();
	}
	
	@Test
	public void shouldHandleCreateWhenStackInRolledBackStatus() throws IOException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, CannotFindVpcException, InvalidParameterException, InterruptedException  {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		String contents = EnvironmentSetupForTests.loadFile(filename);
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Stack stack = new Stack().withStackId(stackId);
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.ROLLBACK_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.getStackId(stackName)).andReturn(stackNameAndId);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		// now proceed with creation
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, "")).
		andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.updateRepositoryFor(stackNameAndId)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(stackNameAndId, result);
		verifyAll();
	}
	
	@Test
	public void shouldHandleCreateWhenStackRolledBackInProgressStatus() throws IOException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, InterruptedException, DuplicateStackException, CannotFindVpcException, InvalidParameterException {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		String contents = EnvironmentSetupForTests.loadFile(filename);
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		Collection<Parameter> creationParameters = new LinkedList<Parameter>();
		List<TemplateParameter> templateParameters = new LinkedList<TemplateParameter>();
		Stack stack = new Stack().withStackId(stackId);
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.ROLLBACK_IN_PROGRESS.toString());	
		EasyMock.expect(cfnRepository.getStackId(stackName)).andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForRollbackComplete(stackNameAndId)).andReturn(StackStatus.ROLLBACK_COMPLETE.toString());
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		// now proceed with creation
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, "")).
		andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.updateRepositoryFor(stackNameAndId)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(stackNameAndId, result);
		verifyAll();
	}

	private void setDeleteExpectations(String stackName,
			StackNameAndId stackNameAndId) throws WrongNumberOfStacksException,
			InterruptedException, NotReadyException, WrongStackStatus {
		EasyMock.expect(cfnRepository.getStackId(stackName)).andReturn(stackNameAndId);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		EasyMock.expect(monitor.waitForDeleteFinished(stackNameAndId)).andReturn(StackStatus.DELETE_COMPLETE.toString());
	}

	public static void addParam(Collection<Parameter> creationParameters, String key, String value) {
		creationParameters.add(new Parameter().withParameterKey(key).withParameterValue(value));
		
	}
	
	private StackNameAndId SetCreateExpectations(String stackName,
			String contents, List<TemplateParameter> templateParameters,
			Collection<Parameter> creationParameters, String comment) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		return SetCreateExpectations(stackName, contents, templateParameters, creationParameters, comment, new LinkedList<Output>());
	}

	private StackNameAndId SetCreateExpectations(String stackName, String contents,
			List<TemplateParameter> templateParameters,
			Collection<Parameter> creationParameters, String comment, Collection<Output> outputs)
			throws NotReadyException, WrongNumberOfStacksException,
			InterruptedException, WrongStackStatus {
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = new Stack().withStackId("stackId");
		if (outputs.size()>0) {
			stack.setOutputs(outputs);
		}
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");	
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, comment)).
			andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(StackStatus.CREATE_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.updateRepositoryFor(stackNameAndId)).andReturn(stack);
		return stackNameAndId;
	}

	private void checkParameterCannotBePassed(String parameterName)
			throws FileNotFoundException, IOException,
			CfnAssistException, InterruptedException {
		Parameter parameter = new Parameter();
		parameter.setParameterKey(parameterName);
		parameter.setParameterValue("test");
		
		Collection<Parameter> parameters = new HashSet<Parameter>();
		parameters.add(parameter);
		
		try {
			aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv, parameters);	
			fail("Should have thrown exception");
		}
		catch (InvalidParameterException exception) {
			// expected
		}
	}
	
}

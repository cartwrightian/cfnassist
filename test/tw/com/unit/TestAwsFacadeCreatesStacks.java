package tw.com.unit;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
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
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.VpcRepository;

@RunWith(EasyMockRunner.class)
public class TestAwsFacadeCreatesStacks extends EasyMockSupport  {
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
	
	public static void addParam(Collection<Parameter> creationParameters, String key, String value) {
		creationParameters.add(new Parameter().withParameterKey(key).withParameterValue(value));		
	}

}

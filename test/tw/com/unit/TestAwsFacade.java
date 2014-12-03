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

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;

import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.BadVPCDeltaIndexException;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

@RunWith(EasyMockRunner.class)
public class TestAwsFacade extends EasyMockSupport {

	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private ELBRepository elbRepository;
	private MonitorStackEvents monitor;
	private String project = projectAndEnv.getProject();
	private EnvironmentTag environmentTag = projectAndEnv.getEnvTag();

	@Before
	public void beforeEachTestRuns() {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		elbRepository = createMock(ELBRepository.class);
		
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository);
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
	public void shouldDeleteNamedStacksNotAssociatedWithLB() throws WrongNumberOfStacksException, NotReadyException, WrongStackStatus, InterruptedException, InvalidParameterException, TooManyELBException {
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		
		Stack stackA = new Stack().withStackName("CfnAssist0057TestsimpleStack").withStackId("idA");
		Stack stackB = new Stack().withStackName("CfnAssist0058TestsimpleStack").withStackId("idB");
		Stack stackC = new Stack().withStackName("CfnAssist0059TestsimpleStack").withStackId("idC"); // only this one associated with LB
		
		List<StackEntry> stacksForProj = new LinkedList<StackEntry>();
		stacksForProj.add(new StackEntry(project, environmentTag, stackA));
		stacksForProj.add(new StackEntry(project, environmentTag, stackB));
		stacksForProj.add(new StackEntry(project, environmentTag, stackC));
		List<Instance> elbInstances = new LinkedList<Instance>();
		
		elbInstances.add(new Instance().withInstanceId("matchingInstanceId"));
		
		EasyMock.expect(elbRepository.findInstancesAssociatedWithLB(projectAndEnv,"typeTag")).andReturn(elbInstances);
		EasyMock.expect(cfnRepository.getStacksMatching(environmentTag,"simpleStack")).andReturn(stacksForProj);	
		EasyMock.expect(cfnRepository.getInstancesFor(stackA.getStackName())).andReturn(createInstancesFor("123"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackB.getStackName())).andReturn(createInstancesFor("567"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackC.getStackName())).andReturn(createInstancesFor("matchingInstanceId"));
		
		setDeleteExpectations(stackA.getStackName(), createNameAndId(stackA));
		setDeleteExpectations(stackB.getStackName(), createNameAndId(stackB));
		
		replayAll();
		aws.tidyNonLBAssocStacks(file, projectAndEnv,"typeTag");
		verifyAll();
	}
	
	@Test
	public void shouldDeleteNamedStacksNotAssociatedWithLBWhileIgnoringStacksWithNoInstances() throws WrongNumberOfStacksException, NotReadyException, WrongStackStatus, InterruptedException, InvalidParameterException, TooManyELBException {
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		
		Stack stackA = new Stack().withStackName("CfnAssist0057TestsimpleStack").withStackId("idA"); // this one has no instances
		Stack stackB = new Stack().withStackName("CfnAssist0058TestsimpleStack").withStackId("idB");
		Stack stackC = new Stack().withStackName("CfnAssist0059TestsimpleStack").withStackId("idC"); // only this one associated with LB
		
		List<StackEntry> stacksForProj = new LinkedList<StackEntry>();
		stacksForProj.add(new StackEntry(project, environmentTag, stackA));
		stacksForProj.add(new StackEntry(project, environmentTag, stackB));
		stacksForProj.add(new StackEntry(project, environmentTag, stackC));
		List<Instance> elbInstances = new LinkedList<Instance>();
		
		elbInstances.add(new Instance().withInstanceId("matchingInstanceId"));
		
		EasyMock.expect(elbRepository.findInstancesAssociatedWithLB(projectAndEnv,"typeTag")).andReturn(elbInstances);
		EasyMock.expect(cfnRepository.getStacksMatching(environmentTag,"simpleStack")).andReturn(stacksForProj);	
		EasyMock.expect(cfnRepository.getInstancesFor(stackA.getStackName())).andReturn(new LinkedList<String>());
		EasyMock.expect(cfnRepository.getInstancesFor(stackB.getStackName())).andReturn(createInstancesFor("567"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackC.getStackName())).andReturn(createInstancesFor("matchingInstanceId"));
		
		setDeleteExpectations(stackB.getStackName(), createNameAndId(stackB));
		
		replayAll();
		aws.tidyNonLBAssocStacks(file, projectAndEnv,"typeTag");
		verifyAll();
	}

	private List<String> createInstancesFor(String id) {
		List<String> instances = new LinkedList<String>();
		instances.add(id);
		return instances;
	}

	private StackNameAndId createNameAndId(Stack stack) {
		return new StackNameAndId(stack.getStackName(), stack.getStackId());
	}

	private void setDeleteExpectations(String stackName,
			StackNameAndId stackNameAndId) throws WrongNumberOfStacksException,
			InterruptedException, NotReadyException, WrongStackStatus {
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		EasyMock.expect(monitor.waitForDeleteFinished(stackNameAndId)).andReturn(StackStatus.DELETE_COMPLETE.toString());
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

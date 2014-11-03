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
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.VpcRepository;

@RunWith(EasyMockRunner.class)
public class TestAwsFacade extends EasyMockSupport {

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
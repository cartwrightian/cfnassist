package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.entity.*;
import tw.com.exceptions.*;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.providers.SavesFile;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestAwsFacade extends EasyMockSupport {

	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private CloudRepository cloudRepository;

	@Before
	public void beforeEachTestRuns() {
		MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		ELBRepository elbRepository = createMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
		IdentityProvider identityProvider = createStrictMock(IdentityProvider.class);
		NotificationSender notificationSender = createStrictMock(NotificationSender.class);

		String regionName = EnvironmentSetupForTests.getRegion().getName();

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider,regionName);
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
	public void shouldSetTagOnVPC() {
		vpcRepository.setVpcTag(projectAndEnv, "tagKey", "tagValue");
		EasyMock.expectLastCall();

		replayAll();
		aws.setTagForVpc(projectAndEnv, "tagKey", "tagValue");
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
		vpcRepository.setVpcIndexTag(projectAndEnv, "99");
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
		List<StackEntry> stacks = new LinkedList<>();
		stacks.add(new StackEntry("proj", projectAndEnv.getEnvTag(), new Stack()));
		EasyMock.expect(cfnRepository.getStacks(projectAndEnv.getEnvTag())).andReturn(stacks);
		
		replayAll();
		List<StackEntry> results = aws.listStacks(projectAndEnv);
		assertEquals(1, results.size());
		verifyAll();
	}

	@Test
	public void shouldListSummaryOfInstancesWithEnv() throws CfnAssistException {
		String idA = "instanceIdA";
		String idB = "instanceIdB";
		List<Tag> tagsA = new LinkedList<>();
		tagsA.add(EnvironmentSetupForTests.createEc2Tag("ENV", "env"));
		List<Tag> tagsB = new LinkedList<>();
		tagsB.add(EnvironmentSetupForTests.createEc2Tag("TAG", "value"));

		com.amazonaws.services.ec2.model.Instance instanceA = new com.amazonaws.services.ec2.model.Instance().
				withInstanceId(idA).withPrivateIpAddress("10.1.2.3").withTags(tagsA);
		com.amazonaws.services.ec2.model.Instance instanceB = new com.amazonaws.services.ec2.model.Instance().
				withInstanceId(idB).withPrivateIpAddress("10.8.7.6").withTags(tagsB);

		List<String> instanceList = new LinkedList<>();
		instanceList.add(idA);
		instanceList.add(idB);
		
		SearchCriteria criteria = new SearchCriteria(projectAndEnv);
		EasyMock.expect(cfnRepository.getAllInstancesFor(criteria)).andReturn(instanceList);
		EasyMock.expect(cloudRepository.getInstanceById(idA)).andReturn(instanceA);
		EasyMock.expect(cloudRepository.getInstanceById(idB)).andReturn(instanceB);
		
		replayAll();
		List<InstanceSummary> results = aws.listInstances(criteria);
		verifyAll();
		
		assertEquals(2, results.size());
		assertTrue(results.contains(new InstanceSummary(idA, "10.1.2.3", tagsA)));
		assertTrue(results.contains(new InstanceSummary(idB, "10.8.7.6", tagsB)));
	}
	
	@Test 
	public void testShouldListStacksNoEnvSupplied() {	
		List<StackEntry> stacks = new LinkedList<>();
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
		List<TemplateParameter> params = new LinkedList<>();
		params.add(new TemplateParameter().withDescription("a parameter"));
		EasyMock.expect(cfnRepository.validateStackTemplate("someContents")).andReturn(params);
		
		replayAll();
		List<TemplateParameter> results = aws.validateTemplate("someContents");
		verifyAll();
		assertEquals(1, results.size());
	}

	@Test
	public void cannotAddEnvParameter() throws IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("env");
	}
	
	@Test
	public void cannotAddvpcParameter() throws IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("vpc");
	}
	
	@Test
	public void cannotAddbuildParameter() throws IOException, CfnAssistException, InterruptedException {
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
		projectAndEnv.addBuildNumber(42);
		String stackName = aws.createStackName(new File(FilesForTesting.SIMPLE_STACK),projectAndEnv);
		
		assertEquals("CfnAssist42TestsimpleStack", stackName);	
	}

	@Test
    public void shouldCreateKeyPairAndSavePrivateKey() throws CfnAssistException {
        SavesFile destination = createStrictMock(SavesFile.class);

		KeyPair keypair = new KeyPair().withKeyName("keyName");
		EasyMock.expect(cloudRepository.createKeyPair("CfnAssist_Test_keypair", destination)).
				andReturn(keypair);

        replayAll();
		KeyPair result = aws.createKeyPair(projectAndEnv, destination);
        verifyAll();

		assertEquals("keyName", result.getKeyName());
    }
	
	private void checkParameterCannotBePassed(String parameterName)
			throws IOException,
			CfnAssistException, InterruptedException {
		Parameter parameter = new Parameter();
		parameter.setParameterKey(parameterName);
		parameter.setParameterValue("test");
		
		Collection<Parameter> parameters = new HashSet<>();
		parameters.add(parameter);
		
		try {
			aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv, parameters);
			fail("Should have thrown exception");
		}
		catch (InvalidStackParameterException exception) {
			// expected
		}
	}
	
}

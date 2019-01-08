package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.entity.InstanceSummary;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.entity.StackEntry;
import tw.com.exceptions.*;
import tw.com.providers.CloudClient;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.providers.SavesFile;
import tw.com.repository.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestAwsFacade extends EasyMockSupport {

	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private CloudRepository cloudRepository;
	private LogRepository logRepository;

	@Before
	public void beforeEachTestRuns() {
		MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		ELBRepository elbRepository = createMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
        logRepository = createStrictMock(LogRepository.class);
		IdentityProvider identityProvider = createStrictMock(IdentityProvider.class);
		NotificationSender notificationSender = createStrictMock(NotificationSender.class);

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, logRepository);
	}
	
	@Test
	public void shouldInitTagsOnVpc() throws CfnAssistException {
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
	public void shouldInitTagsOnVpcThrowIfAlreadyExists() throws CfnAssistException {
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(Vpc.builder().vpcId("existingId").build());
		
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
    public void shouldDeleteLogsOverNWeeksOld() {

		List<String> groups = Arrays.asList("groupA","groupB");
        EasyMock.expect(logRepository.logGroupsFor(projectAndEnv)).andReturn(groups);
        logRepository.removeOldStreamsFor("groupA", Duration.ofDays(42));
        EasyMock.expectLastCall();
        logRepository.removeOldStreamsFor("groupB", Duration.ofDays(42));
        EasyMock.expectLastCall();

        replayAll();
        aws.removeCloudWatchLogsOlderThan(projectAndEnv, 42);
        verifyAll();
    }

    @Test
    public void shouldTagCloudWatchLogWithEnvAndProject() {

        logRepository.tagCloudWatchLog(projectAndEnv, "groupToTag");
        EasyMock.expectLastCall();

        replayAll();
        aws.tagCloudWatchLog(projectAndEnv, "groupToTag");
        verifyAll();
    }

    @Test
    public void shouldFetchLogs() {
        Path filename = Paths.get("filename.log");
        EasyMock.expect(logRepository.fetchLogs(projectAndEnv, Duration.ofHours(42))).
				andReturn(Collections.singletonList(filename));

        replayAll();
        List<Path> result = aws.fetchLogs(projectAndEnv, 42);
        verifyAll();

        assertFalse(result.isEmpty());
        assertEquals(filename, result.get(0));
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
	public void shouldListStacksEnvSupplied() {
		List<StackEntry> stacks = new LinkedList<>();
		stacks.add(new StackEntry("proj", projectAndEnv.getEnvTag(), Stack.builder().build()));
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

		software.amazon.awssdk.services.ec2.model.Instance instanceA = Instance.builder().
				instanceId(idA).privateIpAddress("10.1.2.3").tags(tagsA).build();
		software.amazon.awssdk.services.ec2.model.Instance instanceB = Instance.builder().
				instanceId(idB).privateIpAddress("10.8.7.6").tags(tagsB).build();

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
	public void shouldListStacksNoEnvSupplied() {
		List<StackEntry> stacks = new LinkedList<>();
		stacks.add(new StackEntry("proj", projectAndEnv.getEnvTag(), software.amazon.awssdk.services.cloudformation.model.Stack.builder().build()));
		EasyMock.expect(cfnRepository.getStacks()).andReturn(stacks);
		
		replayAll();
		ProjectAndEnv pattern = new ProjectAndEnv("someProject", "");
		List<StackEntry> results = aws.listStacks(pattern);
		assertEquals(1,results.size());
		verifyAll();
	}
	
	@Test
	public void shouldInvokeValidation() {
		List<TemplateParameter> params = new LinkedList<>();
		params.add(TemplateParameter.builder().description("a parameter").build());
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
	public void shouldCreateStacknameFromEnvAndFileWithDelta() {
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
    public void shouldCreateKeyPairAndTagVPC() throws CfnAssistException {
		Path filename = Paths.get("fileForPem.pem");

        SavesFile destination = createStrictMock(SavesFile.class);
		//KeyPair keypair = new KeyPair().withKeyName("CfnAssist_Test");
        EasyMock.expect(destination.exists(filename)).andReturn(false);

		CloudClient.AWSPrivateKey keypair = new CloudClient.AWSPrivateKey("CfnAssist_Test", "material");
		EasyMock.expect(cloudRepository.createKeyPair("CfnAssist_Test", destination, filename)).
				andReturn(keypair);
        vpcRepository.setVpcTag(projectAndEnv, "keypairname", "CfnAssist_Test");
        EasyMock.expectLastCall();

        replayAll();
		CloudClient.AWSPrivateKey result = aws.createKeyPair(projectAndEnv, destination, filename);
        verifyAll();

		assertEquals("CfnAssist_Test", result.getKeyName());
    }

	@Test
	public void shouldNotCreateKeyPairIfFileAlreadyExists() {
		SavesFile destination = createStrictMock(SavesFile.class);
		Path filename = Paths.get("fileForPem.pem");

		EasyMock.expect(destination.exists(filename)).andReturn(true);

		replayAll();
		try {
			aws.createKeyPair(projectAndEnv, destination, filename);
			fail("should have thrown");
		}
		catch(CfnAssistException expected) {
			// no-op
		}
		verifyAll();
	}

	@Test
    public void shouldFormCorrectTestForSSHCommand() throws CfnAssistException {
        String home = System.getenv("HOME");
        EasyMock.expect(vpcRepository.getVpcTag(AwsFacade.KEYNAME_TAG, projectAndEnv)).andReturn("project_env_keypair");
        EasyMock.expect(vpcRepository.getVpcTag(AwsFacade.NAT_EIP, projectAndEnv)).andReturn("eipAllocationId");
        EasyMock.expect(cloudRepository.getIpFor("eipAllocationId")).andReturn("10.1.2.3");

        replayAll();
        List<String> commandText = aws.createSSHCommand(projectAndEnv, "ec2-user");
        verifyAll();
        StringBuilder result = new StringBuilder();
        commandText.forEach(text -> {
            if (result.length()>0) {
                result.append(" ");
            }
            result.append(text);
        });
        assertEquals(String.format("ssh -i %s/.ssh/project_env.pem ec2-user@10.1.2.3", home), result.toString());
    }
	
	private void checkParameterCannotBePassed(String parameterName)
			throws IOException,
			CfnAssistException, InterruptedException {
		Parameter parameter = Parameter.builder().parameterKey(parameterName).parameterValue("test").build();
		
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

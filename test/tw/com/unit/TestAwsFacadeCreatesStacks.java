package tw.com.unit;

import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import software.amazon.awssdk.services.ec2.model.Vpc;
import com.amazonaws.services.identitymanagement.model.User;
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
import tw.com.entity.CFNAssistNotification;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.entity.Tagging;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static tw.com.EnvironmentSetupForTests.createTemplate;
import static tw.com.EnvironmentSetupForTests.getMainProjectAndEnv;

@RunWith(EasyMockRunner.class)
public class TestAwsFacadeCreatesStacks extends EasyMockSupport  {
	private static final StackStatus CREATE_COMP_STATUS = StackStatus.CREATE_COMPLETE;
	private static final String VPC_ID = "vpcId";
	private final Vpc vpc = Vpc.builder().vpcId(VPC_ID).build();
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private MonitorStackEvents monitor;
	private NotificationSender notificationSender;
	private IdentityProvider identityProvider;
	private CloudRepository cloudRepository;
	private User user;

	@Before
	public void beforeEachTestRuns() {

		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		ELBRepository elbRepository = createMock(ELBRepository.class);
		cloudRepository = createStrictMock(CloudRepository.class);
		notificationSender = createStrictMock(NotificationSender.class);
		identityProvider = createStrictMock(IdentityProvider.class);
        LogRepository logRepository = createStrictMock(LogRepository.class);

		user = new User("path", "userName", "userId", "arn", new Date());

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender,
				identityProvider, logRepository);
	}
	
	@Test
	public void shouldApplySimpleTemplateNoParameters() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateParametersWithOutDescriptions() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(TemplateParameter.builder().parameterKey("noDescription").defaultValue("defaultValue").build());
		Collection<Parameter> creationParameters = new LinkedList<>();

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateOutputParameters() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();
		Collection<Output> outputs = new LinkedList<>();
		outputs.add(Output.builder().description("::CFN_TAG").outputKey("outputKey").outputValue("outputValue").build());
		outputs.add(Output.builder().description("something").outputKey("ignored").outputValue("noThanks").build());

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, "", outputs, zones);
		vpcRepository.setVpcTag(projectAndEnv, "outputKey", "outputValue");
		EasyMock.expectLastCall();
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	private StackNameAndId SetCreateExpectations(String stackName,
                                                 String contents, List<TemplateParameter> templateParameters,
                                                 Collection<Parameter> creationParameters, Map<String, AvailabilityZone> zones) throws CfnAssistException, InterruptedException {
		return SetCreateExpectations(stackName, contents, templateParameters, creationParameters,"", zones); // no comment
	}

	@Test
	public void shouldApplySimpleTemplateNoParametersWithComment() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, "aComment", zones);

        projectAndEnv.setComment("aComment");
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}

	@Test
	public void shouldThrowOnCreateWhenStackExistsAndNotRolledBack() throws IOException, CfnAssistException, InterruptedException  {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		String contents = EnvironmentSetupForTests.loadFile(filename);
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		List<TemplateParameter> templateParameters = new LinkedList<>();
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(vpc);
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
        EasyMock.expect(cfnRepository.stackExists(stackName)).andReturn(true);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(CREATE_COMP_STATUS);	
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		
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
	public void shouldHandleCreateWhenStackInRolledBackStatus() throws IOException, CfnAssistException, InterruptedException  {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		String contents = EnvironmentSetupForTests.loadFile(filename);
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		Collection<Parameter> creationParameters = new LinkedList<>();
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Stack stack = Stack.builder().stackId(stackId).build();
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(vpc);
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
        EasyMock.expect(cfnRepository.stackExists(stackName)).andReturn(true);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.ROLLBACK_COMPLETE);
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		// now proceed with creation
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
		    andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones()).andReturn(zones);

		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);	
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sendMessageId");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(stackNameAndId, result);
		verifyAll();
	}
	
	@Test
	public void shouldHandleCreateWhenStackRolledBackInProgressStatus() throws IOException, CfnAssistException, InterruptedException {
		String stackName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		String contents = EnvironmentSetupForTests.loadFile(filename);
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		Collection<Parameter> creationParameters = new LinkedList<>();
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Stack stack = Stack.builder().stackId(stackId).build();
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(vpc);
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
        EasyMock.expect(cfnRepository.stackExists(stackName)).andReturn(true);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.ROLLBACK_IN_PROGRESS);
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForRollbackComplete(stackNameAndId)).andReturn(StackStatus.ROLLBACK_COMPLETE);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		// now proceed with creation
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
		andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones()).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sendMessageId");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(stackNameAndId, result);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInParamsWithBuild() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssist43TestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplate("vpc"));
		templateParameters.add(createTemplate("env"));
		templateParameters.add(createTemplate("build"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		projectAndEnv.addBuildNumber(43);
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);
		addParam(creationParameters, "build", "43");

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}

	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInParamsWithBuildAndZones() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK_WITH_AZ;
		String stackName = "CfnAssist43TestsimpleStackWithAZ";
		String contents = EnvironmentSetupForTests.loadFile(filename);

		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplate("vpc"));
		templateParameters.add(createTemplate("env"));
		templateParameters.add(createTemplate("build"));
		templateParameters.add(createTemplateWithDescription("zoneA", "::CFN_ZONE_A"));
		templateParameters.add(createTemplateWithDescription("zoneB", "::CFN_ZONE_B"));
		templateParameters.add(createTemplateWithDescription("zoneC", "::CFN_ZONE_C"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		projectAndEnv.addBuildNumber(43);
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);
		addParam(creationParameters, "build", "43");
		addParam(creationParameters, "zoneA", "eu-west-1a");
		addParam(creationParameters, "zoneB", "eu-west-1b");
		addParam(creationParameters, "zoneC", "eu-west-1c");

        Map<String, AvailabilityZone> zones = new HashMap<>();
        zones.put("a", AvailabilityZone.builder().regionName("eu-west-1").zoneName("eu-west-1a").build());
        zones.put("b", AvailabilityZone.builder().regionName("eu-west-1").zoneName("eu-west-1b").build());
        zones.put("c", AvailabilityZone.builder().regionName("eu-west-1").zoneName("eu-west-1c").build());
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);

		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}

	private TemplateParameter createTemplateWithDescription(String key, String desc) {
		return TemplateParameter.builder().parameterKey(key).description(desc).build();
	}

	@Test
	public void shouldApplySimpleTemplateInputParameters() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "subnet", "subnetValue");

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<>();
		addParam(userParams, "subnet", "subnetValue");
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	///////
	// needs environmental variable set to testEnvVar set to testValue
	///////
	@Test
	public void shouldApplySimpleTemplateEnvVarParameters() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplateWithDescription("testEnvVar","::ENV"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "testEnvVar", "testValue");

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<>();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	///////
	// needs environmental variable set to testEnvVar set to testValue
	///////
	@Test
	public void shouldApplySimpleTemplateEnvVarParametersNoEchoSet() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(TemplateParameter.builder().parameterKey("testEnvVar").
				description("::ENV").noEcho(true).build());
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "testEnvVar", "testValue");

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<>();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplyAutoDiscoveryTemplateInputParameters() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplateWithDescription("keyName", "::logicalIdToFind"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "keyName", "foundPhysicalId");
		
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = Stack.builder().stackId("stackId").build();
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(vpc);
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		//EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.UNKNOWN_TO_SDK_VERSION);
        EasyMock.expect(cfnRepository.stackExists(stackName)).andReturn(false);
		// search for the logical id, return the found id
		EasyMock.expect(cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "logicalIdToFind")).andReturn("foundPhysicalId");
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
			andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones()).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sendMessageId");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(stack);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<>();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplyAutoDiscoveryVPCTagParameters() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplateWithDescription("vpcTagKey", "::CFN_TAG"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "vpcTagKey", "foundVpcTagValue");
		
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = Stack.builder().stackId("stackId").build();
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(vpc);
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		//EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.UNKNOWN_TO_SDK_VERSION);
        EasyMock.expect(cfnRepository.stackExists(stackName)).andReturn(false);
		// get the tag from the VPC
		EasyMock.expect(vpcRepository.getVpcTag("vpcTagKey", projectAndEnv)).andReturn("foundVpcTagValue");
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
			andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones()).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);	
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sendMessageId");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(stack);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<>();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateInputParametersNotPassBuild() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssist56TestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "subnet", "subnetValue");
		
		projectAndEnv.addBuildNumber(56);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		List<Parameter> userParams = new LinkedList<>();
		addParam(userParams, "subnet", "subnetValue");
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInAndUserParams() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplate("vpc"));
		templateParameters.add(createTemplate("env"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "subnet", "subnetValue");
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);

		List<Parameter> userParams = new LinkedList<>();
		addParam(userParams, "subnet", "subnetValue");
		replayAll();
		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParams);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	@Test
	public void shouldApplySimpleTemplateEnvAndVpcBuiltInParams() throws CfnAssistException, IOException, InterruptedException {
		String filename = FilesForTesting.SIMPLE_STACK;
		String stackName = "CfnAssistTestsimpleStack";
		String contents = EnvironmentSetupForTests.loadFile(filename);
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(createTemplate("vpc"));
		templateParameters.add(createTemplate("env"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);

        Map<String, AvailabilityZone> zones = new HashMap<>();
        StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
	}
	
	private StackNameAndId SetCreateExpectations(String stackName,
                                                 String contents, List<TemplateParameter> templateParameters,
                                                 Collection<Parameter> creationParameters, String comment, Map<String, AvailabilityZone> zones) throws CfnAssistException, InterruptedException {
        return SetCreateExpectations(stackName, contents, templateParameters, creationParameters, comment, new LinkedList<>(), zones);
	}

	private StackNameAndId SetCreateExpectations(String stackName, String contents,
                                                 List<TemplateParameter> templateParameters,
                                                 Collection<Parameter> creationParameters, String comment, Collection<Output> outputs,
                                                 Map<String, AvailabilityZone> zones)
			throws CfnAssistException, InterruptedException {
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack.Builder stackBuilder = Stack.builder().stackId("stackId");
		if (outputs.size()>0) {
			stackBuilder.outputs(outputs);
		}
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(vpc);
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		//EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.UNKNOWN_TO_SDK_VERSION);
        EasyMock.expect(cfnRepository.stackExists(stackName)).andReturn(false);
        Tagging tagging = new Tagging();
        tagging.setCommentTag(comment);
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, tagging)).
			andReturn(stackNameAndId);
        EasyMock.expect(cloudRepository.getZones()).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sendMessageID");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(stackBuilder.build());
		return stackNameAndId;
	}
	
	public static void addParam(Collection<Parameter> creationParameters, String key, String value) {
		creationParameters.add(Parameter.builder().parameterKey(key).parameterValue(value).build());
	}

}

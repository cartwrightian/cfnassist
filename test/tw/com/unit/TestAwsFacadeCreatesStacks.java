package tw.com.unit;

import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.Vpc;
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
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static tw.com.EnvironmentSetupForTests.getMainProjectAndEnv;

@RunWith(EasyMockRunner.class)
public class TestAwsFacadeCreatesStacks extends EasyMockSupport  {
	private static final String CREATE_COMP_STATUS = StackStatus.CREATE_COMPLETE.toString();
	private static final String VPC_ID = "vpcId";
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private MonitorStackEvents monitor;
	private NotificationSender notificationSender;
	private IdentityProvider identityProvider;
	private CloudRepository cloudRepository;
	private User user;
	private String regionName;

	@Before
	public void beforeEachTestRuns() {
		regionName = EnvironmentSetupForTests.getRegion().toString();

		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		ELBRepository elbRepository = createMock(ELBRepository.class);
		cloudRepository = createStrictMock(CloudRepository.class);
		notificationSender = createStrictMock(NotificationSender.class);
		identityProvider = createStrictMock(IdentityProvider.class);
		
		user = new User("path", "userName", "userId", "arn", new Date());
		String regionName = EnvironmentSetupForTests.getRegion().getName();

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, regionName);
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
		templateParameters.add(new TemplateParameter().withParameterKey("noDescription").withDefaultValue("defaultValue"));
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
		outputs.add(new Output().withDescription("::CFN_TAG").withOutputKey("outputKey").withOutputValue("outputValue"));
		outputs.add(new Output().withDescription("something").withOutputKey("ignored").withOutputValue("noThanks"));

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
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
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
		Stack stack = new Stack().withStackId(stackId);
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.ROLLBACK_COMPLETE.toString());	
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		// now proceed with creation
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
		    andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones(regionName)).andReturn(zones);

		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);	
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS, user);
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
		Stack stack = new Stack().withStackId(stackId);
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		// stack in rolled back status so delete it
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn(StackStatus.ROLLBACK_IN_PROGRESS.toString());	
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForRollbackComplete(stackNameAndId)).andReturn(StackStatus.ROLLBACK_COMPLETE.toString());
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		// now proceed with creation
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
		andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones(regionName)).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS, user);
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
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
		templateParameters.add(new TemplateParameter().withParameterKey("build"));
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
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
		templateParameters.add(new TemplateParameter().withParameterKey("build"));
		templateParameters.add(new TemplateParameter().withParameterKey("zoneA").withDescription("::CFN_ZONE_A"));
		templateParameters.add(new TemplateParameter().withParameterKey("zoneB").withDescription("::CFN_ZONE_B"));
		templateParameters.add(new TemplateParameter().withParameterKey("zoneC").withDescription("::CFN_ZONE_C"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		projectAndEnv.addBuildNumber(43);
		addParam(creationParameters, "env", projectAndEnv.getEnv());
		addParam(creationParameters, "vpc", VPC_ID);
		addParam(creationParameters, "build", "43");
		addParam(creationParameters, "zoneA", "eu-west-1a");
		addParam(creationParameters, "zoneB", "eu-west-1b");
		addParam(creationParameters, "zoneC", "eu-west-1c");

        Map<String, AvailabilityZone> zones = new HashMap<>();
        zones.put("a", new AvailabilityZone().withRegionName("eu-west-1").withZoneName("eu-west-1a"));
        zones.put("b", new AvailabilityZone().withRegionName("eu-west-1").withZoneName("eu-west-1b"));
        zones.put("c", new AvailabilityZone().withRegionName("eu-west-1").withZoneName("eu-west-1c"));
		StackNameAndId stackNameAndId = SetCreateExpectations(stackName, contents, templateParameters, creationParameters, zones);

		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(result, stackNameAndId);
		verifyAll();
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
		templateParameters.add(new TemplateParameter().withParameterKey("testEnvVar").withDescription("::ENV"));
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
		templateParameters.add(new TemplateParameter().withParameterKey("testEnvVar").
				withDescription("::ENV").withNoEcho(true));
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
		templateParameters.add(new TemplateParameter().withParameterKey("keyName").withDescription("::logicalIdToFind"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "keyName", "foundPhysicalId");
		
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = new Stack().withStackId("stackId");
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");	
		// search for the logical id, return the found id
		EasyMock.expect(cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "logicalIdToFind")).andReturn("foundPhysicalId");
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
			andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones(regionName)).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS, user);
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
		templateParameters.add(new TemplateParameter().withParameterKey("vpcTagKey").withDescription("::CFN_TAG"));
		Collection<Parameter> creationParameters = new LinkedList<>();
		addParam(creationParameters, "vpcTagKey", "foundVpcTagValue");
		
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, "stackId");
		Stack stack = new Stack().withStackId("stackId");
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");	
		// get the tag from the VPC
		EasyMock.expect(vpcRepository.getVpcTag("vpcTagKey", projectAndEnv)).andReturn("foundVpcTagValue");
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, new Tagging())).
			andReturn(stackNameAndId);
        Map<String, AvailabilityZone> zones = new HashMap<>();
        EasyMock.expect(cloudRepository.getZones(regionName)).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);	
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS, user);
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
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
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
		templateParameters.add(new TemplateParameter().withParameterKey("vpc"));
		templateParameters.add(new TemplateParameter().withParameterKey("env"));
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
		Stack stack = new Stack().withStackId("stackId");
		if (outputs.size()>0) {
			stack.setOutputs(outputs);
		}
		
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.getStackStatus(stackName)).andReturn("");
        Tagging tagging = new Tagging();
        tagging.setCommentTag(comment);
		EasyMock.expect(cfnRepository.createStack(projectAndEnv, contents, stackName, creationParameters, monitor, tagging)).
			andReturn(stackNameAndId);
        EasyMock.expect(cloudRepository.getZones(regionName)).andReturn(zones);
		EasyMock.expect(monitor.waitForCreateFinished(stackNameAndId)).andReturn(CREATE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, CREATE_COMP_STATUS, user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("sendMessageID");
		EasyMock.expect(cfnRepository.createSuccess(stackNameAndId)).andReturn(stack);
		return stackNameAndId;
	}
	
	public static void addParam(Collection<Parameter> creationParameters, String key, String value) {
		creationParameters.add(new Parameter().withParameterKey(key).withParameterValue(value));		
	}

}

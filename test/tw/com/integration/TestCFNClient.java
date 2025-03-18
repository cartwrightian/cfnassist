package tw.com.integration;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tw.com.*;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.entity.Tagging;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MissingCapabilities;
import tw.com.exceptions.WrongStackStatus;
import tw.com.providers.CloudClient;
import tw.com.providers.CFNClient;
import tw.com.providers.SNSEventSource;
import tw.com.repository.CfnRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

class TestCFNClient {
	
	private static software.amazon.awssdk.services.cloudformation.CloudFormationClient cfnClient;
	private static Ec2Client ec2Client;
	private static DefaultAwsRegionProviderChain regionProvider;

	private PollingStackMonitor polligMonitor;
	private ProjectAndEnv projAndEnv;
	private static VpcRepository vpcRepository;
	private static SNSEventSource snsNotifProvider;
	private CFNClient formationClient;
	private DeletesStacks deletesStacks;
	private Vpc mainTestVPC;

	@BeforeAll
	public static void onceBeforeClassRuns() {
		ec2Client = EnvironmentSetupForTests.createEC2Client();
		regionProvider = new DefaultAwsRegionProviderChain();
		vpcRepository = new VpcRepository(new CloudClient(ec2Client, regionProvider));
		cfnClient = EnvironmentSetupForTests.createCFNClient();
		SnsClient snsClient = EnvironmentSetupForTests.createSNSClient();
		SqsClient sqsClient = EnvironmentSetupForTests.createSQSClient();
		snsNotifProvider = new SNSEventSource(snsClient, sqsClient);
		
		new DeletesStacks(cfnClient).ifPresent("queryStackTest").
			ifPresent("createStackTest").
			ifPresent("createIAMStackTest").act();
	}
	
	//@Rule public TestName test = new TestName();
	private SNSMonitor snsMonitor;

	@BeforeEach
	public void beforeEachTestRuns() throws MissingArgumentException, CfnAssistException, InterruptedException {
		formationClient = new CFNClient(cfnClient);
		CloudClient cloudClient = new CloudClient(ec2Client, regionProvider);
		CloudRepository cloudRepository = new CloudRepository(cloudClient);
		CfnRepository cfnRepository = new CfnRepository(formationClient, cloudRepository, EnvironmentSetupForTests.PROJECT);
		polligMonitor = new PollingStackMonitor(cfnRepository );
		snsMonitor = new SNSMonitor(snsNotifProvider, cfnRepository, cfnRepository);
		snsMonitor.init();
		
		deletesStacks = new DeletesStacks(cfnClient);
		projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

		mainTestVPC = vpcRepository.getCopyOfVpc(projAndEnv);
	}
	
	@AfterEach
	public void afterEachTestHasRun() {
		deletesStacks.act();
	}
	
	@Test
    void shouldCreateAndDeleteSimpleStack(TestInfo testInfo) throws IOException, CfnAssistException, InterruptedException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SIMPLE_STACK), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createStackTest";
		String comment = testInfo.getDisplayName();
		List<Tag> expectedTags = EnvironmentSetupForTests.createExpectedStackTags(comment,-1, "CfnAssist");
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor, createTagging(comment));
		deletesStacks.ifPresent(stackName);
		
		Assertions.assertEquals(stackName, nameAndId.getStackName());
		
		StackStatus status = polligMonitor.waitForCreateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.CREATE_COMPLETE, status);

		DescribeStacksResponse queryResult = cfnClient.describeStacks(DescribeStacksRequest.builder().
				stackName(stackName).build());
		Assertions.assertEquals(1, queryResult.stacks().size());
		Stack stack = queryResult.stacks().get(0);
		Assertions.assertEquals(stack.stackId(), nameAndId.getStackId());
		
		List<Tag> stackTags = stack.tags();
		Assertions.assertEquals(expectedTags.size(), stackTags.size());
		assert(stackTags.containsAll(expectedTags));
		
		///////
		// now delete
		formationClient.deleteStack(stackName);
		status = polligMonitor.waitForDeleteFinished(nameAndId);
		Assertions.assertEquals(StackStatus.DELETE_COMPLETE, status);

		status = formationClient.currentStatus(stackName);
		Assertions.assertEquals(StackStatus.DELETE_COMPLETE, status);
		
		try {
			cfnClient.describeStacks(DescribeStacksRequest.builder().stackName(stackName).build());
			Assertions.fail("throws if stack does not exist");
		}
		catch(CloudFormationException expectedException) {
			Assertions.assertEquals(400, expectedException.statusCode());
		}	
	}

    private Tagging createTagging(String comment) {
        Tagging tagging = new Tagging();
        tagging.setCommentTag(comment);
        return tagging;
    }

    @Test
    void shouldThrowIfCapabilitiesNotSetCorrectly(TestInfo test) throws IOException, CfnAssistException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.STACK_IAM_CAP), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createIAMStackTest";
		String comment = test.getDisplayName();
		deletesStacks.ifPresent(stackName);
		// should trigger capability exception
		try {
			formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor, createTagging(comment));
			Assertions.fail("Was expecting MissingCapabilities");
		}
		catch(MissingCapabilities expected) {
			// expected exception
		}	
	}
	
	@Test
    void shouldCreateAndDeleteSimpleStackNeedingIAMCapbility(TestInfo test) throws IOException, CfnAssistException, InterruptedException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.STACK_IAM_CAP), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createIAMStackTest"; 
		String comment = test.getDisplayName();
		projAndEnv.setUseCapabilityIAM();
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor, createTagging(comment));
		deletesStacks.ifPresent(stackName);
				
		// this  create will fail, but due to lack of user perms and not because of capabilities exception
		try {	
			polligMonitor.waitForCreateFinished(nameAndId);
			Assertions.fail("show have failed to create");
		}
		catch(WrongStackStatus expected) {
			// expected
		}
	}

	@Test
    void shouldQueryCreatedStack(TestInfo test) throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.vpcId();
		String cidr = "10.0.42.0/24";
		
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_CIDR_PARAM), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		parameters.add(Parameter.builder().parameterKey("cidr").parameterValue(cidr).build());
		String stackName = "queryStackTest";

		deletesStacks.ifPresent(stackName);

		Assertions.assertFalse(formationClient.stackExists(stackName));
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, 
				polligMonitor, createTagging(test.getDisplayName()));

		StackStatus status = polligMonitor.waitForCreateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.CREATE_COMPLETE, status);
		Assertions.assertTrue(formationClient.stackExists(stackName));
		
		// query all stacks
		List<Stack> resultStacks = formationClient.describeAllStacks();
		Assertions.assertTrue(resultStacks.size()>0);
		boolean seen = false;
		for(Stack candidate : resultStacks) {
			if (candidate.stackName().equals("queryStackTest")) {
				seen = true;
				break;
			}
		}
		Assertions.assertTrue(seen);
		
		// query single stack
		Stack resultStack = formationClient.describeStack("queryStackTest");
		Assertions.assertEquals("queryStackTest", resultStack.stackName());
		
		// query events
		List<StackEvent> resultEvents = formationClient.describeStackEvents("queryStackTest");
		Assertions.assertTrue(resultEvents.size()>0); // TODO what can we check here?
		
		// query resources
		assertCIDR(nameAndId, cidr, vpcId);
	}

	@Test
    void shouldDetectDrift(TestInfo test) throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.vpcId();
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_YAML), Charset.defaultCharset());

		Collection<Parameter> parameters = createStandardParameters(vpcId);
		String stackName = "createStackTest";
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor,
				createTagging(test.getDisplayName()));
		deletesStacks.ifPresent(stackName);

		Assertions.assertEquals(stackName, nameAndId.getStackName());

		StackStatus stackStatus = polligMonitor.waitForCreateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.CREATE_COMPLETE, stackStatus);

		CFNClient.DriftStatus status = getStackDrift(stackName);
		Assertions.assertEquals(StackDriftStatus.IN_SYNC, status.getStackDriftStatus());
		Assertions.assertEquals(0, status.getDriftedStackResourceCount());

		// find the id of the subnet from the stack
		List<StackResource> resources = formationClient.describeStackResources(stackName);
		Assertions.assertEquals(1, resources.size());
		StackResource subnetResource = resources.get(0);
		Assertions.assertEquals("testSubnet", subnetResource.logicalResourceId());
		String subnetId = subnetResource.physicalResourceId();

		// delete the subnet directly
		DeleteSubnetRequest deleteSubnetRequest = DeleteSubnetRequest.builder().subnetId(subnetId).build();
		ec2Client.deleteSubnet(deleteSubnetRequest);

		// wait for deletion
		DescribeSubnetsRequest describeSubnetsRequest = DescribeSubnetsRequest.builder().subnetIds(subnetId).build();
		try {
			while(!ec2Client.describeSubnets(describeSubnetsRequest).subnets().isEmpty()) {
				Thread.sleep(1000);
			}
		} catch (Ec2Exception expected) {
			Assertions.assertEquals(400, expected.statusCode());
		}

		// now check if stack has drifted
		status = getStackDrift(stackName);
		Assertions.assertEquals(StackDriftStatus.DRIFTED, status.getStackDriftStatus());
		Assertions.assertEquals(1, status.getDriftedStackResourceCount());

	}

	private CFNClient.DriftStatus getStackDrift(String stackName) throws InterruptedException {
		String detectionId = formationClient.detectDrift(stackName);
		while (formationClient.driftDetectionInProgress(detectionId)) {
			Thread.sleep(5*1000);
		}
		return formationClient.getDriftDetectionResult(stackName, detectionId);
	}

	@Test
    void shouldCreateAndThenUpdateAStack(TestInfo test) throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.vpcId();
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_JSON), Charset.defaultCharset());

		Collection<Parameter> parameters = createStandardParameters(vpcId);

		String stackName = "createStackTest";
		deletesStacks.ifPresent(stackName);

		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor,
                createTagging(test.getDisplayName()));

		Assertions.assertEquals(stackName, nameAndId.getStackName());

		StackStatus status = polligMonitor.waitForCreateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.CREATE_COMPLETE, status);

		assertCIDR(nameAndId, "10.0.42.0/24", vpcId);

		/////
		// now update
		
		String newContents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_DELTA), Charset.defaultCharset());
		nameAndId = formationClient.updateStack(newContents, parameters, polligMonitor, stackName);
		
		Assertions.assertEquals(stackName, nameAndId.getStackName());
		
		status = polligMonitor.waitForUpdateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.UPDATE_COMPLETE, status);

		assertCIDR(nameAndId, "10.0.99.0/24", vpcId);
	}
	
	@Test
    void shouldCreateAndThenUpdateAStackAddingSNS(TestInfo test) throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.vpcId();
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_JSON), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		String stackName = "createStackTest";
		deletesStacks.ifPresent(stackName);

		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, 
				polligMonitor, createTagging(test.getDisplayName()));

		Assertions.assertEquals(stackName, nameAndId.getStackName());
		
		StackStatus status = polligMonitor.waitForCreateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.CREATE_COMPLETE, status);
		
		assertCIDR(nameAndId, "10.0.42.0/24", vpcId);
		
		/////
		// now update

		String newContents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_DELTA), Charset.defaultCharset());
		nameAndId = formationClient.updateStack(newContents, parameters, snsMonitor, stackName);
		
		Assertions.assertEquals(stackName, nameAndId.getStackName());
		
		status = snsMonitor.waitForUpdateFinished(nameAndId);
		Assertions.assertEquals(StackStatus.UPDATE_COMPLETE, status);

		assertCIDR(nameAndId, "10.0.99.0/24", vpcId);	
		
	}
	
	@Test
    void shouldValidateTemplatesJSON() throws IOException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_JSON), Charset.defaultCharset());
		List<TemplateParameter> result =  formationClient.validateTemplate(contents);
		
		Assertions.assertEquals(4, result.size());
		
		int i;
		for(i=0; i<4; i++) {
			TemplateParameter parameter = result.get(i);
			if (parameter.parameterKey().equals("zoneA")) break;
		}
		TemplateParameter zoneAParameter = result.get(i);
		
		Assertions.assertEquals("zoneA", zoneAParameter.parameterKey());
		Assertions.assertEquals("eu-west-1a", zoneAParameter.defaultValue());
		Assertions.assertEquals("zoneADescription", zoneAParameter.description());
	}


	@Test
    void shouldValidateTemplatesYAML() throws IOException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_YAML), Charset.defaultCharset());
		List<TemplateParameter> result =  formationClient.validateTemplate(contents);

		Assertions.assertEquals(4, result.size());

		int i;
		for(i=0; i<4; i++) {
			TemplateParameter parameter = result.get(i);
			if (parameter.parameterKey().equals("zoneA")) break;
		}
		TemplateParameter zoneAParameter = result.get(i);

		Assertions.assertEquals("zoneA", zoneAParameter.parameterKey());
		Assertions.assertEquals("eu-west-1a", zoneAParameter.defaultValue());
		Assertions.assertEquals("zoneADescription", zoneAParameter.description());
	}

	private void assertCIDR(StackNameAndId nameAndId, String initialCidr, String vpcId) {
		List<StackResource> resultResources = formationClient.describeStackResources(nameAndId.getStackName());
		Assertions.assertEquals(1, resultResources.size());
		String subnetId = resultResources.get(0).physicalResourceId();
		Subnet subnet = getSubnetDetails(subnetId);	
		Assertions.assertEquals(initialCidr, subnet.cidrBlock());
		Assertions.assertEquals(vpcId, subnet.vpcId());
	}
	
	private Subnet getSubnetDetails(String physicalId) {
		DescribeSubnetsRequest describeSubnetsRequest = DescribeSubnetsRequest.builder().subnetIds(physicalId).build();
		DescribeSubnetsResponse result = ec2Client.describeSubnets(describeSubnetsRequest);
		Assertions.assertEquals(1, result.subnets().size());
		return result.subnets().get(0);
	}
	
	private Collection<Parameter> createStandardParameters(String vpcId) {
		Collection<Parameter> parameters = new LinkedList<>();
		parameters.add(Parameter.builder().parameterKey("env").parameterValue("Test").build());
		parameters.add(Parameter.builder().parameterKey("vpc").parameterValue(vpcId).build());
		return parameters;
	}
	


}

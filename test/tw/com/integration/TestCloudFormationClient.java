package tw.com.integration;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.rules.TestName;
import tw.com.*;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.entity.Tagging;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MissingCapabilities;
import tw.com.exceptions.WrongStackStatus;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;
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

import static org.junit.Assert.*;

public class TestCloudFormationClient {
	
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client ec2Client;
	
	private PollingStackMonitor polligMonitor;
	private ProjectAndEnv projAndEnv;
	private CloudClient cloudClient;
	private static VpcRepository vpcRepository;
	private static SNSEventSource snsNotifProvider;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	CloudFormationClient formationClient;
	private DeletesStacks deletesStacks;
	private Vpc mainTestVPC;
	
	@BeforeClass
	public static void onceBeforeClassRuns() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		vpcRepository = new VpcRepository(new CloudClient(ec2Client));
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		snsClient =  EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
		snsNotifProvider = new SNSEventSource(snsClient, sqsClient);
		
		new DeletesStacks(cfnClient).ifPresent("queryStackTest").
			ifPresent("createStackTest").
			ifPresent("createIAMStackTest").act();
	}
	
	@Rule public TestName test = new TestName();
	private SNSMonitor snsMonitor;

	@Before
	public void beforeEachTestRuns() throws MissingArgumentException, CfnAssistException, InterruptedException {
		formationClient = new CloudFormationClient(cfnClient);
		cloudClient = new CloudClient(ec2Client);
		CloudRepository cloudRepository = new CloudRepository(cloudClient);
		CfnRepository cfnRepository = new CfnRepository(formationClient, cloudRepository, EnvironmentSetupForTests.PROJECT);
		polligMonitor = new PollingStackMonitor(cfnRepository );
		snsMonitor = new SNSMonitor(snsNotifProvider, cfnRepository);
		snsMonitor.init();
		
		deletesStacks = new DeletesStacks(cfnClient);
		projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

		mainTestVPC = vpcRepository.getCopyOfVpc(projAndEnv);
	}
	
	@After
	public void afterEachTestHasRun() {
		deletesStacks.act();
	}
	
	@Test
	public void shouldCreateAndDeleteSimpleStack() throws IOException, CfnAssistException, InterruptedException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SIMPLE_STACK), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createStackTest";
		String comment = test.getMethodName();
		List<Tag> expectedTags = EnvironmentSetupForTests.createExpectedStackTags(comment,-1, "CfnAssist");
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor, createTagging(comment));
		deletesStacks.ifPresent(stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		String status = polligMonitor.waitForCreateFinished(nameAndId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		DescribeStacksResult queryResult = cfnClient.describeStacks(new DescribeStacksRequest().withStackName(stackName));
		assertEquals(1, queryResult.getStacks().size());
		Stack stack = queryResult.getStacks().get(0);
		assertEquals(stack.getStackId(), nameAndId.getStackId());
		
		List<Tag> stackTags = stack.getTags();
		assertEquals(expectedTags.size(), stackTags.size());
		assert(stackTags.containsAll(expectedTags));
		
		///////
		// now delete
		formationClient.deleteStack(stackName);
		status = polligMonitor.waitForDeleteFinished(nameAndId);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
		
		try {
			cfnClient.describeStacks(new DescribeStacksRequest().withStackName(stackName));
			fail("throws if stack does not exist");
		}
		catch(AmazonServiceException expectedException) {
			assertEquals(400, expectedException.getStatusCode());
		}	
	}

    private Tagging createTagging(String comment) {
        Tagging tagging = new Tagging();
        tagging.setCommentTag(comment);
        return tagging;
    }

    @Test
	public void shouldThrowIfCapabilitiesNotSetCorrectly() throws IOException, CfnAssistException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.STACK_IAM_CAP), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createIAMStackTest";
		String comment = test.getMethodName();
		deletesStacks.ifPresent(stackName);
		// should trigger capability exception
		try {
			formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor, createTagging(comment));
			fail("Was expecting MissingCapabilities");
		}
		catch(MissingCapabilities expected) {
			// expected exception
		}	
	}
	
	@Test
	public void shouldCreateAndDeleteSimpleStackNeedingIAMCapbility() throws IOException, CfnAssistException, InterruptedException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.STACK_IAM_CAP), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createIAMStackTest"; 
		String comment = test.getMethodName();
		projAndEnv.setUseCapabilityIAM();
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor, createTagging(comment));
		deletesStacks.ifPresent(stackName);
				
		// this  create will fail, but due to lack of user perms and not because of capabilities exception
		try {	
			polligMonitor.waitForCreateFinished(nameAndId);
			fail("show have failed to create");
		}
		catch(WrongStackStatus expected) {
			// expected
		}
	}

	@Test
	public void shouldQueryCreatedStack() throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.getVpcId();
		String cidr = "10.0.10.0/24";
		
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_CIDR_PARAM), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		parameters.add(new Parameter().withParameterKey("cidr").withParameterValue(cidr));
		String stackName = "queryStackTest";
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, 
				polligMonitor, createTagging(test.getMethodName()));
		deletesStacks.ifPresent(nameAndId);
		
		String status = polligMonitor.waitForCreateFinished(nameAndId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		// query all stacks
		List<Stack> resultStacks = formationClient.describeAllStacks();
		assertTrue(resultStacks.size()>0);
		boolean seen = false;
		for(Stack candidate : resultStacks) {
			if (candidate.getStackName().equals("queryStackTest")) {
				seen = true;
				break;
			}
		}
		assertTrue(seen);
		
		// query single stack
		Stack resultStack = formationClient.describeStack("queryStackTest");
		assertEquals("queryStackTest", resultStack.getStackName());
		
		// query events
		List<StackEvent> resultEvents = formationClient.describeStackEvents("queryStackTest");
		assertTrue(resultEvents.size()>0); // TODO what can we check here?
		
		// query resources
		assertCIDR(nameAndId, cidr, vpcId);
	}
	
	@Test
	public void shouldCreateAndThenUpdateAStack() throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.getVpcId();
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		String stackName = "createStackTest";
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, polligMonitor,
                createTagging(test.getMethodName()));
		deletesStacks.ifPresent(stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		String status = polligMonitor.waitForCreateFinished(nameAndId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		assertCIDR(nameAndId, "10.0.10.0/24", vpcId);
		
		/////
		// now update
		
		String newContents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_DELTA), Charset.defaultCharset());
		nameAndId = formationClient.updateStack(newContents, parameters, polligMonitor, stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		status = polligMonitor.waitForUpdateFinished(nameAndId);
		assertEquals(StackStatus.UPDATE_COMPLETE.toString(), status);

		assertCIDR(nameAndId, "10.0.99.0/24", vpcId);	
		
		// TODO Check TAGS?
	}
	
	@Test
	public void shouldCreateAndThenUpdateAStackAddingSNS() throws IOException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.getVpcId();
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		String stackName = "createStackTest";
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, 
				polligMonitor, createTagging(test.getMethodName()));
		deletesStacks.ifPresent(stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		String status = polligMonitor.waitForCreateFinished(nameAndId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		assertCIDR(nameAndId, "10.0.10.0/24", vpcId);
		
		/////
		// now update

		String newContents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_DELTA), Charset.defaultCharset());
		nameAndId = formationClient.updateStack(newContents, parameters, snsMonitor, stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		status = snsMonitor.waitForUpdateFinished(nameAndId);
		assertEquals(StackStatus.UPDATE_COMPLETE.toString(), status);

		assertCIDR(nameAndId, "10.0.99.0/24", vpcId);	
		
		// TODO Check TAGS?
	}
	
	@Test
	public void shouldValidateTemplates() throws IOException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK), Charset.defaultCharset());
		List<TemplateParameter> result =  formationClient.validateTemplate(contents);
		
		assertEquals(4, result.size());
		
		int i;
		for(i=0; i<4; i++) {
			TemplateParameter parameter = result.get(i);
			if (parameter.getParameterKey().equals("zoneA")) break;		
		}
		TemplateParameter zoneAParameter = result.get(i);
		
		assertEquals("zoneA", zoneAParameter.getParameterKey());
		assertEquals("eu-west-1a", zoneAParameter.getDefaultValue());
		assertEquals("zoneADescription", zoneAParameter.getDescription());
	}

	private void assertCIDR(StackNameAndId nameAndId, String initialCidr, String vpcId) {
		List<StackResource> resultResources = formationClient.describeStackResources(nameAndId.getStackName());
		assertEquals(1, resultResources.size());
		String subnetId = resultResources.get(0).getPhysicalResourceId();
		Subnet subnet = getSubnetDetails(subnetId);	
		assertEquals(initialCidr, subnet.getCidrBlock());
		assertEquals(vpcId, subnet.getVpcId());		
	}
	
	private Subnet getSubnetDetails(String physicalId) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<String> subnetIds = new LinkedList<>();
		subnetIds.add(physicalId);
		describeSubnetsRequest.setSubnetIds(subnetIds);
		DescribeSubnetsResult result = ec2Client.describeSubnets(describeSubnetsRequest);
		assertEquals(1, result.getSubnets().size());	
		
		return result.getSubnets().get(0);
	}
	
	private Collection<Parameter> createStandardParameters(String vpcId) {
		Collection<Parameter> parameters = new LinkedList<>();
		parameters.add(new Parameter().withParameterKey("env").withParameterValue("Test"));
		parameters.add(new Parameter().withParameterKey("vpc").withParameterValue(vpcId));
		return parameters;
	}
	


}

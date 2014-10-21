package tw.com.integration;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.PollingStackMonitor;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;
import tw.com.repository.CfnRepository;
import tw.com.repository.VpcRepository;

public class TestCloudFormationClient {
	
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client ec2Client;
	
	private static VpcRepository vpcRepository;
	CloudFormationClient formationClient;
	private DeletesStacks deletesStacks;
	private Vpc mainTestVPC;
	
	@BeforeClass
	public static void onceBeforeClassRuns() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		vpcRepository = new VpcRepository(new CloudClient(ec2Client));
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		
		new DeletesStacks(cfnClient).ifPresent("queryStackTest").ifPresent("createStackTest").act();
	}
	
	@Rule public TestName test = new TestName();
	private PollingStackMonitor monitor;
	private ProjectAndEnv projAndEnv;
	
	@Before
	public void beforeEachTestRuns() {
		formationClient = new CloudFormationClient(cfnClient);
		CfnRepository cfnRepository = new CfnRepository(formationClient, EnvironmentSetupForTests.PROJECT);
		monitor = new PollingStackMonitor(cfnRepository );
		
		deletesStacks = new DeletesStacks(cfnClient);
		projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

		mainTestVPC = vpcRepository.getCopyOfVpc(projAndEnv);
	}
	
	@After
	public void afterEachTestHasRun() {
		deletesStacks.act();
	}
	
	@Test
	public void shouldCreateAndDeleteStack() throws IOException, NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SIMPLE_STACK), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters("someVpcId");
		String stackName = "createStackTest";
		String comment = test.getMethodName();
		List<Tag> expectedTags = EnvironmentSetupForTests.createExpectedStackTags(comment); // no comment
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, monitor, comment);
		deletesStacks.ifPresent(stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		String status = monitor.waitForCreateFinished(nameAndId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		DescribeStacksResult queryResult = cfnClient.describeStacks(new DescribeStacksRequest().withStackName(stackName));
		assertEquals(1,queryResult.getStacks().size());
		Stack stack = queryResult.getStacks().get(0);
		assertEquals(stack.getStackId(), nameAndId.getStackId());
		
		List<Tag> stackTags = stack.getTags();
		assertEquals(expectedTags.size(), stackTags.size());
		assert(stackTags.containsAll(expectedTags));
		
		///////
		// now delete
		formationClient.deleteStack(stackName);
		status = monitor.waitForDeleteFinished(nameAndId);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
		
		try {
			queryResult = cfnClient.describeStacks(new DescribeStacksRequest().withStackName(stackName));
			fail("throws if stack does not exist");
		}
		catch(AmazonServiceException expectedException) {
			assertEquals(400, expectedException.getStatusCode());
		}	
	}

	private Collection<Parameter> createStandardParameters(String vpcId) {
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		parameters.add(new Parameter().withParameterKey("env").withParameterValue("Test"));
		parameters.add(new Parameter().withParameterKey("vpc").withParameterValue(vpcId));
		return parameters;
	}
	
	@Test
	public void shouldQueryCreatedStack() throws FileNotFoundException, IOException, InvalidParameterException, CfnAssistException, InterruptedException {
		String vpcId = mainTestVPC.getVpcId();
		String cidr = "10.0.10.0/24";
		
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_CIDR_PARAM), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		parameters.add(new Parameter().withParameterKey("cidr").withParameterValue(cidr));
		String stackName = "queryStackTest";
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, monitor, test.getMethodName());
		deletesStacks.ifPresent(nameAndId);
		
		String status = monitor.waitForCreateFinished(nameAndId);
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
	public void shouldCreateAndThenUpdateAStack() throws IOException, NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		String vpcId = mainTestVPC.getVpcId();
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK), Charset.defaultCharset());
		
		Collection<Parameter> parameters = createStandardParameters(vpcId);
		String stackName = "createStackTest";
		StackNameAndId nameAndId = formationClient.createStack(projAndEnv, contents, stackName, parameters, monitor, test.getMethodName());
		deletesStacks.ifPresent(stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		String status = monitor.waitForCreateFinished(nameAndId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		assertCIDR(nameAndId, "10.0.10.0/24", vpcId);
		
		/////
		// now update
		
		String newContents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK_DELTA), Charset.defaultCharset());
		nameAndId = formationClient.updateStack(newContents, parameters, monitor, stackName);
		
		assertEquals(stackName, nameAndId.getStackName());
		
		status = monitor.waitForUpdateFinished(nameAndId);
		assertEquals(StackStatus.UPDATE_COMPLETE.toString(), status);

		assertCIDR(nameAndId, "10.0.99.0/24", vpcId);	
		
		// TODO Check TAGS?
	}
	
	@Test
	public void shouldValidateTemplates() throws IOException {
		String contents = FileUtils.readFileToString(new File(FilesForTesting.SUBNET_STACK), Charset.defaultCharset());
		List<TemplateParameter> result =  formationClient.validateTemplate(contents);
		
		assertEquals(4, result.size());
		
		int i = 0;
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
		Collection<String> subnetIds = new LinkedList<String>();
		subnetIds.add(physicalId);
		describeSubnetsRequest.setSubnetIds(subnetIds);
		DescribeSubnetsResult result = ec2Client.describeSubnets(describeSubnetsRequest);
		assertEquals(1, result.getSubnets().size());	
		
		return result.getSubnets().get(0);
	}


}

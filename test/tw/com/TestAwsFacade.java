package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestAwsFacade {

	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	
	private AwsProvider aws;
	private ProjectAndEnv projectAndEnv;
	private MonitorStackEvents monitor;
	private VpcRepository vpcRepository;
	private CfnRepository cfnRepository;


	private DeletesStacks deletesStacks;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
	}
	
	@Rule public TestName testName = new TestName();

	
	@Before
	public void beforeTestsRun() {
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new PollingStackMonitor(cfnRepository);	
		aws = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		aws.setCommentTag(testName.getMethodName());
		projectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		
		deletesStacks = new DeletesStacks(cfnClient).ifPresent("CfnAssistTestsimpleStack").ifPresent("CfnAssistTestsubnet");
		deletesStacks.act();
	}
	
	@After 
	public void afterEachTestRuns() {
		deletesStacks.act();
	}

	@Test
	public void testReturnCorrectParametersFromValidation() throws FileNotFoundException, IOException {
		List<TemplateParameter> result = aws.validateTemplate(new File(FilesForTesting.SUBNET_STACK));
		
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
	public void createsSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		StackId result = aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);	
		
		assertNotNull(result.getStackId());
		assertTrue(result.getStackId().length()>0);
		assertEquals("CfnAssistTestsimpleStack", result.getStackName());
	}
	
	@Test
	public void createsAndThenUpdateSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		Vpc vpc = vpcRepository.getCopyOfVpc(projectAndEnv);
		
		StackId before = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), projectAndEnv);
		List<Subnet> beforeSubnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		
		StackId after = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK_DELTA), projectAndEnv);
		List<Subnet> afterSubnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		
		deletesStacks.ifPresent(before);
		
		assertEquals(before.getStackId(), after.getStackId()); // an update, so stack ID is the same
		
		assertEquals(1, beforeSubnets.size());
		assertEquals("10.0.10.0/24", beforeSubnets.get(0).getCidrBlock());
		assertEquals(1, afterSubnets.size()); // should have updated an existing subnet
		assertEquals("10.0.99.0/24", afterSubnets.get(0).getCidrBlock());
	}
	
	@Test
	public void catchIssueWithMonitoringUpdateWithSNSWhenOriginalStackHadNoSNSNotif() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException, MissingArgumentException {
		
		aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), projectAndEnv);
		
		MonitorStackEvents snsMonitor = new SNSMonitor(snsClient, sqsClient);
		snsMonitor.init();
		AwsFacade snsAws = new AwsFacade(snsMonitor, cfnClient, cfnRepository, vpcRepository);
		try {		
			snsAws.applyTemplate(new File(FilesForTesting.SUBNET_STACK_DELTA), projectAndEnv);
			fail("Should have thrown");
		}
		catch(InvalidParameterException expected) {
			// expected this as SNS notifications will not be available on the stack
		}	
	}
	
	@Test
	public void deleteSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		
		DescribeStacksResult before = cfnClient.describeStacks();
		File templateFile = new File(FilesForTesting.SIMPLE_STACK);
		aws.applyTemplate(templateFile, projectAndEnv);	
		
		aws.deleteStackFrom(templateFile, projectAndEnv);
		DescribeStacksResult after = cfnClient.describeStacks();
		
		assertEquals(before.getStacks().size(), after.getStacks().size());
	}
	
	@Test
	public void deleteSimpleStackWithBuildNumber() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		
		DescribeStacksResult before = cfnClient.describeStacks();
		File templateFile = new File(FilesForTesting.SIMPLE_STACK);
		projectAndEnv.addBuildNumber("987");
		aws.applyTemplate(templateFile, projectAndEnv);	
		
		aws.deleteStackFrom(templateFile, projectAndEnv);
		DescribeStacksResult after = cfnClient.describeStacks();
		
		deletesStacks.ifPresent("CfnAssist987TestsimpleStack");
		assertEquals(before.getStacks().size(), after.getStacks().size());
	}
	
	@Test 
	public void shouldIncludeBuildNumberWhenFormingStackname() {
		projectAndEnv.addBuildNumber("042");
		String stackName = aws.createStackName(new File(FilesForTesting.SIMPLE_STACK),projectAndEnv);
		
		deletesStacks.ifPresent("CfnAssist042TestsimpleStack");
		assertEquals("CfnAssist042TestsimpleStack", stackName);	
	}
	
	@Test
	public void catchesStackAlreadyExistingAsExpected() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		StackId stackId = aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);	
		
		try {
			aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);
			fail("Should have thrown exception");
		} 
		catch(DuplicateStackException expected) {
			// expected
		}
		
		deletesStacks.ifPresent(stackId);
	}
	
	@Test
	public void handlesRollBackCompleteStatusAutomatically() throws FileNotFoundException, CfnAssistException, NotReadyException, IOException, InvalidParameterException, InterruptedException {

		StackId id = null;
		try {
			aws.applyTemplate(new File(FilesForTesting.CAUSEROLLBACK), projectAndEnv);
			fail("should have thrown");
		} catch (WrongStackStatus exception) {
			id = exception.getStackId();
		}	
		monitor.waitForRollbackComplete(id);
		try {
			aws.applyTemplate(new File(FilesForTesting.CAUSEROLLBACK), projectAndEnv);
			fail("should have thrown");
		} catch (WrongStackStatus exception) {
			// expected a create fail, and *not* a duplicate stack exception
			id = exception.getStackId();
		}	
		deletesStacks.ifPresent("CfnAssistTestcausesRollBack");
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

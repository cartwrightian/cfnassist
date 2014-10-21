package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.MissingArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.SNSMonitor;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;
import tw.com.providers.SNSEventSource;
import tw.com.repository.CfnRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestExecuteScriptsInOrderFromDir {
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client ec2Client;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	private DeletesStacks deletesStacks;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	
	private static String env = EnvironmentSetupForTests.ENV;
	private static String proj = EnvironmentSetupForTests.PROJECT;
	private static CloudClient cloudClient;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj,env);

	ArrayList<String> expectedList = new ArrayList<String>();
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
		cloudClient = new CloudClient(ec2Client);
	}
	
	@Rule public TestName test = new TestName();

	@Before 
	public void beforeAllTestsRun() throws IOException, CannotFindVpcException {
		createExpectedNames();	
		deletesStacks = new DeletesStacks(cfnClient).
				ifPresent("CfnAssistTest01createSubnet").
				ifPresent("CfnAssistTest02createAcls").
				ifPresent("CfnAssistTest03createRoutes");
		deletesStacks.act();
		
		cfnRepository = new CfnRepository(new CloudFormationClient(cfnClient), EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(cloudClient);	
	}
		
	@After
	public void afterAllTestsHaveRun() throws IOException, CfnAssistException {	
		deletesStacks.act();
	}
	
	private AwsFacade createFacade(CfnRepository cfnRepository,
			VpcRepository vpcRepository, MonitorStackEvents monitor) throws CannotFindVpcException {
		AwsFacade aws = new AwsFacade(monitor, cfnRepository, vpcRepository);
		aws.setCommentTag(test.getMethodName());
		aws.resetDeltaIndex(mainProjectAndEnv);
		return aws;
	}
	
	// TODO : into monitor tests
	@Test
	public void shouldApplyDeltasAsStackUpdatesPollingMonitor() throws FileNotFoundException, InvalidParameterException, IOException, CfnAssistException, InterruptedException {
		PollingStackMonitor monitor = new PollingStackMonitor(cfnRepository);
		AwsFacade aws = createFacade(cfnRepository, vpcRepository, monitor);
		
		applyDeltasAsStackUpdates(aws);
	}
	
	// TODO : into monitor tests
	@Test
	public void shouldApplyDeltasAsStackUpdatesSNSMonitor() throws FileNotFoundException, InvalidParameterException, IOException, CfnAssistException, InterruptedException, MissingArgumentException {
		SNSMonitor monitor = new SNSMonitor(new SNSEventSource(snsClient, sqsClient), cfnRepository);
		monitor.init();
		AwsFacade aws = createFacade(cfnRepository, vpcRepository, monitor);
		
		applyDeltasAsStackUpdates(aws);
	}

	private void applyDeltasAsStackUpdates(AwsFacade aws)
			throws InvalidParameterException, FileNotFoundException,
			IOException, CfnAssistException, InterruptedException {
		List<StackNameAndId> stackIds = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER, mainProjectAndEnv);
		assertEquals(2, stackIds.size());
		
		// a delta updates an exitsing stack, so the stack id should be the same
		assertEquals(stackIds.get(0), stackIds.get(1));
		
		VpcRepository vpcRepository = new VpcRepository(cloudClient);
		Vpc vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		assertEquals(1, subnets.size());
		assertEquals("10.0.99.0/24", subnets.get(0).getCidrBlock()); // check CIDR from the delta script not the orginal one
	}

	private String formName(String part) {
		return proj+env+part;
	}

	private void createExpectedNames() {
		expectedList.add(formName("01createSubnet"));
		expectedList.add(formName("02createAcls"));
	}

}

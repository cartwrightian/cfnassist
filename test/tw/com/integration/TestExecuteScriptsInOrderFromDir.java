package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.CfnRepository;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.ProjectAndEnv;
import tw.com.SNSMonitor;
import tw.com.StackId;
import tw.com.VpcRepository;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestExecuteScriptsInOrderFromDir {
	
	private static final String THIRD_FILE = "03createRoutes.json";
	private Path thirdFile;
	
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client ec2Client;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	private DeletesStacks deletesStacks;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	
	private static String env = EnvironmentSetupForTests.ENV;
	private static String proj = EnvironmentSetupForTests.PROJECT;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj,env);

	ArrayList<String> expectedList = new ArrayList<String>();
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
	}
	
	@Rule public TestName test = new TestName();

	@Before 
	public void beforeAllTestsRun() throws IOException, CannotFindVpcException {
		createExpectedNames();	
		deletesStacks = new DeletesStacks(cfnClient).
				ifPresent("CfnAssistTest01createSubnet").
				ifPresent("CfnAssistTest02createAcls");
		deletesStacks.act();
		
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(ec2Client);
		
	}

	private AwsFacade createFacade(CfnRepository cfnRepository,
			VpcRepository vpcRepository, MonitorStackEvents monitor) throws CannotFindVpcException {
		AwsFacade aws = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		aws.setCommentTag(test.getMethodName());
		aws.resetDeltaIndex(mainProjectAndEnv);
		return aws;
	}
	
	@After
	public void afterAllTestsHaveRun() throws IOException, CfnAssistException {	

		deletesStacks.act();
	}

	@Test
	public void shouldCreateTheStacksRequiredOnly() throws CfnAssistException, InterruptedException, FileNotFoundException, InvalidParameterException, IOException {
		PollingStackMonitor monitor = new PollingStackMonitor(cfnRepository);
		AwsFacade aws = createFacade(cfnRepository, vpcRepository, monitor);
		
		List<StackId> stackIds = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, mainProjectAndEnv);
		
		assertEquals(expectedList.size(), stackIds.size());
		
		for(int i=0; i<expectedList.size(); i++) {
			StackId stackId = stackIds.get(i);
			assertEquals(expectedList.get(i), stackId.getStackName());
			// TODO should just be a call to get current status because applyTemplatesFromFolder is a blocking call
			String status = monitor.waitForCreateFinished(stackId);
			assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		}
		
		// we are up to date, should not apply the files again
		stackIds = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, mainProjectAndEnv);
		assertEquals(0, stackIds.size());
		
		// copy in one extra files to dir
		thirdFile = copyInFile(THIRD_FILE);
		
		stackIds = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, mainProjectAndEnv);
		assertEquals(1, stackIds.size());
		
		expectedList.add(formName("03createRoutes"));
		assertEquals(expectedList.get(2), stackIds.get(0).getStackName());
		
		// tidy up the stacks
		List<String> deletedStacks = aws.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, mainProjectAndEnv);
		assertEquals(3, deletedStacks.size());
		assert(deletedStacks.containsAll(expectedList));
		
		int finalIndex = aws.getDeltaIndex(mainProjectAndEnv);
		assertEquals(0, finalIndex);
		
		Files.deleteIfExists(thirdFile);
	}
	
	@Test
	public void shouldApplyDeltasAsStackUpdatesPollingMonitor() throws FileNotFoundException, InvalidParameterException, IOException, CfnAssistException, InterruptedException {
		PollingStackMonitor monitor = new PollingStackMonitor(cfnRepository);
		AwsFacade aws = createFacade(cfnRepository, vpcRepository, monitor);
		
		applyDeltasAsStackUpdates(aws);
	}
	
	@Test
	public void shouldApplyDeltasAsStackUpdatesSNSMonitor() throws FileNotFoundException, InvalidParameterException, IOException, CfnAssistException, InterruptedException, MissingArgumentException {
		SNSMonitor monitor = new SNSMonitor(snsClient, sqsClient);
		monitor.init();
		AwsFacade aws = createFacade(cfnRepository, vpcRepository, monitor);
		
		applyDeltasAsStackUpdates(aws);
	}

	private void applyDeltasAsStackUpdates(AwsFacade aws)
			throws InvalidParameterException, FileNotFoundException,
			IOException, CfnAssistException, InterruptedException {
		List<StackId> stackIds = aws.applyTemplatesFromFolder(FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER, mainProjectAndEnv);
		assertEquals(2, stackIds.size());
		
		// a delta updates an exitsing stack, so the stack id should be the same
		assertEquals(stackIds.get(0), stackIds.get(1));
		
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		Vpc vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		assertEquals(1, subnets.size());
		assertEquals("10.0.99.0/24", subnets.get(0).getCidrBlock()); // check CIDR from the delta script not the orginal one
	}
	
	private Path copyInFile(String filename) throws IOException {
		Path srcFile = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "holding", filename);
		Path destFile = FileSystems.getDefault().getPath(FilesForTesting.ORDERED_SCRIPTS_FOLDER, filename);
		Files.deleteIfExists(destFile);

		FileUtils.copyFile(srcFile.toFile(), destFile.toFile());
		return destFile;
	}

	private String formName(String part) {
		return proj+env+part;
	}

	private void createExpectedNames() {
		expectedList.add(formName("01createSubnet"));
		expectedList.add(formName("02createAcls"));
	}

}

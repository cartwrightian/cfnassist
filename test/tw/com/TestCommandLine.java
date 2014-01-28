package tw.com;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.commandline.Main;
import tw.com.exceptions.CannotFindVpcException;

public class TestCommandLine {

	private static final int FAILURE_STATUS = -1;
	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private Vpc altEnvVPC;
	private VpcRepository vpcRepository;
	private ProjectAndEnv altProjectAndEnv;
	private AmazonEC2Client directClient;
	private AmazonCloudFormationClient cfnClient;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		vpcRepository = new VpcRepository(EnvironmentSetupForTests.createEC2Client(credentialsProvider));
		altProjectAndEnv = EnvironmentSetupForTests.getAltProjectAndEnv();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		altEnvVPC = vpcRepository.getCopyOfVpc(altProjectAndEnv);
		if (altEnvVPC==null) {
			altEnvVPC=vpcRepository.getCopyOfVpc("vpc-21e5ee43");
		}
		
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, EnvironmentSetupForTests.TEMPORARY_STACK);
	}
	
	@Test
	public void testInvokeInitViaCommandLine() {
		
		EnvironmentSetupForTests.clearVpcTags(directClient, altEnvVPC);
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ALT_ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init", altEnvVPC.getVpcId()
				};
		Main main = new Main(args);
		int result = main.parse();

		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeInitViaCommandLineMissingValue() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init" 
				};
		Main main = new Main(args);
		assertEquals(FAILURE_STATUS,main.parse());
	}
	
	@Test
	public void testInvokeResetViaCommandLine() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-reset"
				};
		Main main = new Main(args);
		assertEquals(0,main.parse());
	}
	
	@Test
	public void testInvokeResetViaCommandLineWithExtraParams() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-reset",
				"-parameters", "testA=123;testB=123"
				};
		Main main = new Main(args);
		assertEquals(0,main.parse());
	}
	
	@Test
	public void testInvokeViaCommandLineWithExtraIncorrectParams() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-reset",
				"-parameters", "testA=123;testB"
				};
		Main main = new Main(args);
		assertEquals(FAILURE_STATUS,main.parse());
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFile() throws InterruptedException, TimeoutException {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", EnvironmentSetupForTests.SUBNET_FILENAME,
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteStack(cfnClient, "CfnAssistTestsubnet", true);
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFileAndBuildNumber() throws InterruptedException, TimeoutException {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", EnvironmentSetupForTests.SUBNET_FILENAME,
				"-build", "001"
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteStack(cfnClient, "CfnAssist001Testsubnet", false);
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFileAndArnForSNS() throws InterruptedException, TimeoutException {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", EnvironmentSetupForTests.SUBNET_FILENAME,
				"-arn", EnvironmentSetupForTests.ARN_FOR_TESTING
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteStack(cfnClient, "CfnAssistTestsubnet", false);
		assertEquals(0,result);
	}
	
	@Test
	public void shouldNotAllowBuildParameterWithDirAction() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", EnvironmentSetupForTests.FOLDER_PATH,
				"-build", "001"
				};
		Main main = new Main(args);
		int result = main.parse();
		assertEquals(FAILURE_STATUS, result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFilePassedInParam() throws InterruptedException, TimeoutException {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", EnvironmentSetupForTests.SUBNET_WITH_PARAM_FILENAME,
				"-parameters", "zoneA=eu-west-1a"
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteStack(cfnClient, "CfnAssistTestsubnetWithParam", true);
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollback() throws CannotFindVpcException, InterruptedException, TimeoutException {
		String[] argsDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", EnvironmentSetupForTests.FOLDER_PATH
				};
		Main main = new Main(argsDeploy);
		int result = main.parse();
		assertEquals(0,result);
		
		String[] rollbackDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-rollback", EnvironmentSetupForTests.FOLDER_PATH
				};
		main = new Main(rollbackDeploy);
		result = main.parse();
		
		//clean up as needed
		vpcRepository.initAllTags(altEnvVPC.getVpcId(), altProjectAndEnv);
		cfnClient.setRegion(EnvironmentSetupForTests.getRegion());
		EnvironmentSetupForTests.deleteStack(cfnClient , "CfnAssistTest01createSubnet",false);
		EnvironmentSetupForTests.deleteStack(cfnClient , "CfnAssistTest02createAcls",false);
		
		// check
		assertEquals(0,result);
	}
	
	@Ignore("cant find way to label at existing stack via apis")
	@Test
	public void testInvokeViaCommandLineTagExistingStack() throws IOException {
		EnvironmentSetupForTests.createTemporaryStack(cfnClient, altEnvVPC.getVpcId());
		
		String[] argslabelStack = {
				"-env", EnvironmentSetupForTests.ENV,
				"-project", EnvironmentSetupForTests.PROJECT,
				"-labelstack", EnvironmentSetupForTests.TEMPORARY_STACK 
		};
		Main main = new Main(argslabelStack);
		int result = main.parse();	
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, EnvironmentSetupForTests.TEMPORARY_STACK);		
		assertEquals(0, result);
	}

}

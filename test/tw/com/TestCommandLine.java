package tw.com;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.commandline.Main;

public class TestCommandLine {

	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private Vpc vpc;
	private VpcRepository vpcRepository;
	private ProjectAndEnv altProjectAndEnv;
	private AmazonEC2Client directClient;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		vpcRepository = new VpcRepository(credentialsProvider, EnvironmentSetupForTests.getRegion());
		altProjectAndEnv = EnvironmentSetupForTests.getAltProjectAndEnv();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		vpc = vpcRepository.getCopyOfVpc(altProjectAndEnv);
	}
	
	@Test
	public void testInvokeInitViaCommandLine() {
		
		EnvironmentSetupForTests.clearVpcTags(directClient, vpc);
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ALT_ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init", vpc.getVpcId()
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
		assertEquals(-1,main.parse());
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
		assertEquals(-1,main.parse());
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFile() {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", EnvironmentSetupForTests.SUBNET_FILENAME,
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteStack(cfnClient, "CfnAssistTestsubnet");
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFilePassedInParam() {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", EnvironmentSetupForTests.SUBNET_WITH_PARAM_FILENAME,
				"-parameters", "zoneA=eu-west-1a"
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteStack(cfnClient, "CfnAssistTestsubnetWithParam");
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollback() throws CannotFindVpcException {
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
		vpcRepository.initAllTags(vpc.getVpcId(), altProjectAndEnv);
		AmazonCloudFormationClient cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(EnvironmentSetupForTests.getRegion());
		EnvironmentSetupForTests.deleteStack(cfnClient , "CfnAssistTest01createSubnet");
		EnvironmentSetupForTests.deleteStack(cfnClient , "CfnAssistTest02createAcls");
		
		// check
		assertEquals(0,result);
	}

}

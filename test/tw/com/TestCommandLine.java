package tw.com;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.commandline.Main;

public class TestCommandLine {

	private DefaultAWSCredentialsProviderChain credentialsProvider;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
	}
	
	@Test
	@Ignore("Reached limit on number of vpcs")
	public void testInvokeInitViaCommandLine() {
		AmazonEC2Client directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		Vpc tempVpc = EnvironmentSetupForTests.createVpc(directClient);
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init", tempVpc.getVpcId()
				};
		Main main = new Main(args);
		int result = main.parse();
		EnvironmentSetupForTests.deleteVpc(directClient, tempVpc);
		
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
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollback() {
		String[] argsDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", EnvironmentSetupForTests.FOLDER_PATH
				};
		Main main = new Main(argsDeploy);
		assertEquals(0,main.parse());
		
		String[] rollbackDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-rollback", EnvironmentSetupForTests.FOLDER_PATH
				};
		main = new Main(rollbackDeploy);
		assertEquals(0,main.parse());
	}

}

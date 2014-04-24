package tw.com;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.commandline.Main;
import tw.com.exceptions.CannotFindVpcException;

public class TestCommandLine {

	private static final int FAILURE_STATUS = -1;
	private Vpc altEnvVPC;
	private VpcRepository vpcRepository;
	private ProjectAndEnv altProjectAndEnv;
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private DeletesStacks deletesStacks;
	
	@BeforeClass
	public static void beforeAllTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
	}
	
	@Rule public TestName test = new TestName();
	String testName = "";

	@Before
	public void beforeEveryTestRun() {
		vpcRepository = new VpcRepository(ec2Client);
		altProjectAndEnv = EnvironmentSetupForTests.getAltProjectAndEnv();
		
		altEnvVPC = EnvironmentSetupForTests.findAltVpc(vpcRepository);	
		deletesStacks = new DeletesStacks(cfnClient);
		deletesStacks.ifPresent(EnvironmentSetupForTests.TEMPORARY_STACK)
			.ifPresent("CfnAssistTest01createSubnet")
			.ifPresent("CfnAssistTest02createAcls")
			.ifPresent("CfnAssistTestsimpleStack");
		deletesStacks.act();
		testName = test.getMethodName();
	}
	
	@After
	public void afterEachTestIsRun() {
		deletesStacks.act();
	}
	
	@Test
	public void testInvokeInitViaCommandLine() throws InterruptedException {
		
		EnvironmentSetupForTests.clearVpcTags(ec2Client, altEnvVPC);
		
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
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0,result);
	}
	
	@Test
	public void testDeleteViaCommandLineDeployWithFile() throws InterruptedException, TimeoutException {
		String[] createArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		Main main = new Main(createArgs);
		int createResult = main.parse();
		assertEquals(0,createResult);
		
		String[] deleteArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-delete", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		main = new Main(deleteArgs);
		int deleteResult = main.parse();
		assertEquals(0,deleteResult);
		
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFileAndBuildNumber() throws InterruptedException, TimeoutException {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SIMPLE_STACK,
				"-build", "876",
				"-comment", testName
				};
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssist876TestsimpleStack");
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeploySwitchELBInstances() throws InterruptedException, TimeoutException {
		String[] createIns = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-build", "876",
				"-file", FilesForTesting.ELB_AND_INSTANCE,
				"-comment", testName
				};
		Main main = new Main(createIns);
		main.parse();
				
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-build", "876",
				"-elbUpdate", "web"
				};
		main = new Main(args);
		int result = main.parse();
		
		deletesStacks.ifPresent("CfnAssist876TestelbAndInstance");
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFileAndSNS() throws InterruptedException, TimeoutException {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SIMPLE_STACK,
				"-sns", 
				"-comment", testName
				};
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0,result);
	}
	
	@Test
	public void shouldNotAllowBuildParameterWithDirAction() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", FilesForTesting.ORDERED_SCRIPTS_FOLDER,
				"-build", "001"
				};
		Main main = new Main(args);
		int result = main.parse();
		assertEquals(FAILURE_STATUS, result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFilePassedInParam() throws InterruptedException, TimeoutException {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SUBNET_WITH_PARAM,
				"-parameters", "zoneA=eu-west-1a",
				"-comment", testName
				};
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsubnetWithParam");
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollback() throws CannotFindVpcException, InterruptedException, TimeoutException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenRollback(projAndEnv, "", FilesForTesting.ORDERED_SCRIPTS_FOLDER);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollbackWithSNS() throws CannotFindVpcException, InterruptedException, TimeoutException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenRollback(projAndEnv, "-sns", FilesForTesting.ORDERED_SCRIPTS_FOLDER);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirDeltasAndThenRollback() throws CannotFindVpcException, InterruptedException, TimeoutException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenRollback(projAndEnv, "", FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirDeltasAndThenRollbackWithSNS() throws CannotFindVpcException, InterruptedException, TimeoutException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenRollback(projAndEnv, "-sns", FilesForTesting.ORDERED_SCRIPTS_WITH_DELTAS_FOLDER);
	}

	private void invokeForDirAndThenRollback(ProjectAndEnv projAndEnv,
			String sns, String orderedScriptsFolder) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
		
		String[] argsDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", orderedScriptsFolder,
				"-comment", testName,
				sns
				};
		Main main = new Main(argsDeploy);
		int result = main.parse();
		assertEquals("deploy failed",0,result);
		
		String[] rollbackDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-rollback", orderedScriptsFolder,
				sns
				};
		main = new Main(rollbackDeploy);
		result = main.parse();
		
		//clean up as needed
		vpcRepository.initAllTags(altEnvVPC.getVpcId(), altProjectAndEnv);
		cfnClient.setRegion(EnvironmentSetupForTests.getRegion());
		
		// check
		assertEquals("rollback failed",0,result);
	}
	
	@Ignore("cant find way to label at existing stack via apis")
	@Test
	public void testInvokeViaCommandLineTagExistingStack() throws IOException {
		EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, altEnvVPC.getVpcId(),"");
		
		String[] argslabelStack = {
				"-env", EnvironmentSetupForTests.ENV,
				"-project", EnvironmentSetupForTests.PROJECT,
				"-labelstack", "todoNotWorking"
		};
		Main main = new Main(argslabelStack);
		int result = main.parse();	
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");		
		assertEquals(0, result);
	}

}

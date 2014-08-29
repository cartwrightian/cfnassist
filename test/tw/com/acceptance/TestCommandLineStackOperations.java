package tw.com.acceptance;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.ProjectAndEnv;
import tw.com.VpcRepository;
import tw.com.commandline.Main;
import tw.com.exceptions.CannotFindVpcException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

public class TestCommandLineStackOperations {

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
		EnvironmentSetupForTests.getMainProjectAndEnv();
		
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
	public void testInvokeViaCommandLineDeployWithFile() throws InterruptedException, TimeoutException {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0,result);
	}
	
	@Test
	public void testListStacks() throws InterruptedException, TimeoutException {
        PrintStream origStream = System.out;

		String[] create = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		Main main = new Main(create);
		int status = main.parse();
		
		String[] list = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-ls"
				};
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintStream output = new PrintStream(stream);
		System.setOut(output);
		
		main = new Main(list);
		status = main.parse();
		
		String result = stream.toString();
		String lines[] = result.split("\\r?\\n");

		boolean found=false;
		for(String line : lines) {
			found = line.equals("CfnAssistTestsimpleStack\tCfnAssist\tTest");
			if (found) break;
		}
		assert(found);

		System.setOut(origStream);
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0, status);
	}
	
	@Test
	public void testDeleteViaCommandLineDeployWithFileAndBuildNumber() throws InterruptedException, TimeoutException {
		String[] createArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-build", "0915",
				"-comment", testName
				};
		Main main = new Main(createArgs);
		int createResult = main.parse();
		assertEquals(0,createResult);
		
		String[] deleteArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-delete", FilesForTesting.SIMPLE_STACK,
				"-build", "0915",
				"-comment", testName
				};
		main = new Main(deleteArgs);
		int deleteResult = main.parse();
		assertEquals(0,deleteResult);
		
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
	}
	
	@Test
	public void testDeleteViaCommandLineDeployWithFile() throws InterruptedException, TimeoutException {
		String[] createArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		Main main = new Main(createArgs);
		int createResult = main.parse();
		assertEquals(0,createResult);
		
		String[] deleteArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-build", "876",
				"-file", FilesForTesting.ELB_AND_INSTANCE,
				"-comment", testName
				};
		Main main = new Main(createIns);
		main.parse();
				
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
	public void testInvokeViaCommandLineDeployWithFilePassedInParam() throws InterruptedException, TimeoutException {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
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
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-labelstack", "todoNotWorking"
		};
		Main main = new Main(argslabelStack);
		int result = main.parse();	
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");		
		assertEquals(0, result);
	}

}
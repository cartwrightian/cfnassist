package tw.com.acceptance;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import tw.com.CLIArgBuilder;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.commandline.Main;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.providers.CloudClient;
import tw.com.repository.VpcRepository;

import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

public class TestCommandLineStackOperationsLegacy {

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
	
	@Rule
    public Timeout globalTimeout = new Timeout(5*60*1000);
	
	@Before
	public void beforeEveryTestRun() {
		vpcRepository = new VpcRepository(new CloudClient(ec2Client));
		altProjectAndEnv = EnvironmentSetupForTests.getAltProjectAndEnv();
		EnvironmentSetupForTests.getMainProjectAndEnv();
		
		altEnvVPC = EnvironmentSetupForTests.findAltVpc(vpcRepository);	
		deletesStacks = new DeletesStacks(cfnClient);
		deletesStacks.ifPresent(EnvironmentSetupForTests.TEMPORARY_STACK)
			.ifPresent("CfnAssistTest01createSubnet")
			.ifPresent("CfnAssistTest02createAcls")
			.ifPresent("CfnAssistTestsimpleStack")
			.ifPresent("CfnAssistTestsubnet")
			.ifPresent("CfnAssist876TestelbAndInstance")
            .ifPresent("CfnAssistTestsimpleStackWithAZ");
		deletesStacks.act();
		testName = test.getMethodName();
	}
	
	@After
	public void afterEachTestIsRun() {
		deletesStacks.act();
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
		invokeForDirAndThenRollback(projAndEnv, "", FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString());
	}

	@Test
	public void testInvokeViaCommandLineDeployWholeDirDeltasAndThenRollbackWithSNS() throws CannotFindVpcException, InterruptedException, TimeoutException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		invokeForDirAndThenRollback(projAndEnv, "-sns", FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString());
	}

	@Test
	public void testInvokeViaCommandLineAndThenStepBackWithSNS() throws CannotFindVpcException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
		
		String[] argsDeploy = CLIArgBuilder.deployFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns", testName);
		Main main = new Main(argsDeploy);
		int result = main.parse();
		assertEquals("deploy failed",0,result);
		
		String[] stepback = CLIArgBuilder.stepback(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns");
		
		// step back first stack
		main = new Main(stepback);
		int resultA = main.parse();
		// step back second stack
		main = new Main(stepback);
		int resultB = main.parse();
		
		vpcRepository.initAllTags(altEnvVPC.getVpcId(), altProjectAndEnv);
		
		assertEquals("first stepback failed",0,resultA);
		assertEquals("second stepback failed",0,resultB);
	}

	@Deprecated
	private void invokeForDirAndThenRollback(ProjectAndEnv projAndEnv,
			String sns, String orderedScriptsFolder) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
		
		String[] argsDeploy = CLIArgBuilder.deployFromDir(orderedScriptsFolder, sns, testName);
		Main main = new Main(argsDeploy);
		int result = main.parse();
		assertEquals("deploy failed",0,result);
		
		String[] rollbackDeploy = CLIArgBuilder.rollbackFromDir(orderedScriptsFolder, sns);
		main = new Main(rollbackDeploy);
		result = main.parse();
		
		//clean up as needed
		vpcRepository.initAllTags(altEnvVPC.getVpcId(), altProjectAndEnv);
		//cfnClient.setRegion(EnvironmentSetupForTests.getRegion());
		
		// check
		assertEquals("rollback failed",0,result);
	}
	

}

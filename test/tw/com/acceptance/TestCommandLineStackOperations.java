package tw.com.acceptance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Vpc;
import tw.com.CLIArgBuilder;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.commandline.Main;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.providers.CloudClient;
import tw.com.repository.VpcRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCommandLineStackOperations {

	private Vpc altEnvVPC;
	private VpcRepository vpcRepository;
	private ProjectAndEnv altProjectAndEnv;
	private static Ec2Client ec2Client;
	private static CloudFormationClient cfnClient;
	private DeletesStacks deletesStacks;
	
	@BeforeAll
	public static void beforeAllTestsRun() {
		ec2Client = EnvironmentSetupForTests.createEC2Client();
		cfnClient = EnvironmentSetupForTests.createCFNClient();
	}
	
	String testName = "";
	
//	@Rule
//    public Timeout globalTimeout = new Timeout(5*60, TimeUnit.SECONDS);
	
	@BeforeEach
	public void beforeEveryTestRun(TestInfo info) {
		vpcRepository = new VpcRepository(new CloudClient(ec2Client, new DefaultAwsRegionProviderChain()));
		altProjectAndEnv = EnvironmentSetupForTests.getAltProjectAndEnv();

		altEnvVPC = EnvironmentSetupForTests.findAltVpc(vpcRepository);	
		deletesStacks = new DeletesStacks(cfnClient);
		deletesStacks.ifPresent(EnvironmentSetupForTests.TEMPORARY_STACK)
			.ifPresent("CfnAssistTest01createSubnet")
			.ifPresent("CfnAssistTest02createAcls")
			.ifPresent("CfnAssistTestsimpleStack")
			.ifPresent("CfnAssistTestsubnetWithParam")
			.ifPresent("CfnAssistTestsubnet")
			.ifPresent("CfnAssist876TestelbAndInstance")
            .ifPresent("CfnAssistTestsimpleStackWithAZ")
			.ifPresent("CfnAssist876TesttargetGroupAndInstance");

		deletesStacks.act();
		testName = info.getDisplayName();
	}
	
	@AfterEach
	public void afterEachTestIsRun() {
		deletesStacks.act();
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFile() {
		String[] args = CLIArgBuilder.createSimpleStack(testName);

		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0, result);
	}
		
	@Test
	public void testInvokeViaCommandLineDeployWithFileAndBuildNumber() {
		String[] args = CLIArgBuilder.createSimpleStackWithBuildNumber(testName, "876");
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssist876TestsimpleStack");
		assertEquals(0,result);
	}
	
	@Test
	public void testListStacks() {
        PrintStream origStream = System.out;

		String[] create = CLIArgBuilder.createSimpleStack(testName);
		Main main = new Main(create);
		main.parse();
		
		String[] list = CLIArgBuilder.listStacks();
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintStream output = new PrintStream(stream);
		System.setOut(output);
		
		main = new Main(list);
		int status = main.parse();

		System.setOut(origStream);

		String outputText = stream.toString(Charset.defaultCharset());
		CLIArgBuilder.checkForExpectedLine(outputText, "CfnAssistTestsimpleStack", "CfnAssist", "Test", StackStatus.CREATE_COMPLETE.toString());

		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0, status);
	}
	
	@Test
	public void testInvokeDiagramGenViaCLI() {
		String[] create = CLIArgBuilder.createDiagrams("./diagrams");
		Main main = new Main(create);
		int status = main.parse();
		assertEquals(0, status);
	}
	
	@Test
	public void testDeleteViaCommandLineDeployWithFileAndBuildNumber() {
		String buildNumber = "0915";
		
		String[] createArgs = CLIArgBuilder.createSimpleStackWithBuildNumber(testName, buildNumber);
		Main main = new Main(createArgs);
		int createResult = main.parse();
		assertEquals(0,createResult);
		
		String[] deleteArgs = CLIArgBuilder.deleteSimpleStackWithBuildNumber(buildNumber);
		main = new Main(deleteArgs);
		int deleteResult = main.parse();
		assertEquals(0,deleteResult);
		
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
	}
	
	@Test
	public void testDeleteViaCommandLineDeployWithFile() {
		String[] createArgs = CLIArgBuilder.createSimpleStack(testName);
		Main main = new Main(createArgs);
		int createResult = main.parse();
		assertEquals(0,createResult);
		
		String[] deleteArgs = CLIArgBuilder.deleteSimpleStack();
		main = new Main(deleteArgs);
		int deleteResult = main.parse();
		assertEquals(0,deleteResult);
		
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
	}

    @Test
    public void shouldDeleteViaCommandLineDeployWithName() {
        String[] createArgs = CLIArgBuilder.createSimpleStack(testName);
        Main main = new Main(createArgs);
        int createResult = main.parse();
        assertEquals(0,createResult);

        String[] deleteArgs = CLIArgBuilder.deleteByNameSimpleStack("simpleStack");
        main = new Main(deleteArgs);
        int deleteResult = main.parse();
        assertEquals(0,deleteResult);

        deletesStacks.ifPresent("CfnAssistTestsimpleStack");
    }
	
	@Test
	public void testInvokeViaCommandLineDeploySwitchELBInstancesAndWhitelistIP() {
		Integer buildNumber = 876;
		String typeTag = "web";
		
		String[] createELBAndInstance = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-build", buildNumber.toString(),
				"-file", FilesForTesting.ELB_AND_INSTANCE,
				"-comment", testName
				};

		Main main = new Main(createELBAndInstance);
		int result = main.parse();
		// delete here because subsequent ops in this tests not against the ELB or instance?? TODO
		deletesStacks.ifPresent("CfnAssist876TestelbAndInstance");
		assertEquals(0,result);
		
		String[] updateELB = CLIArgBuilder.updateELB(typeTag, buildNumber);
		main = new Main(updateELB);
		result = main.parse();
		assertEquals(0,result);
		
		Integer port = 8080;
		String[] allowlistCurrentIP = CLIArgBuilder.allowlistCurrentIP(typeTag, port);
		main = new Main(allowlistCurrentIP);
		result = main.parse();
		assertEquals(0,result);
		
		String[] blockCurrentIP = CLIArgBuilder.blockCurrentIP(typeTag, port);
		main = new Main(blockCurrentIP);
		result = main.parse();
		assertEquals(0,result);
	}

	@Test
	public void testInvokeTargetGroupUpdate() {
		Integer buildNumber = 876;
		Integer port = 9997;

		String[] createTargetGroupAndInstance = {
				"-env", EnvironmentSetupForTests.ENV,
				"-project", EnvironmentSetupForTests.PROJECT,
				"-build", buildNumber.toString(),
				"-file", FilesForTesting.TARGET_GROUP_AND_INSTANCE,
				"-comment", testName
		};

		Main main = new Main(createTargetGroupAndInstance);
		int result = main.parse();
		assertEquals(0,result);

		String[] updateTargetGroup = CLIArgBuilder.updateTargetGroup("web", buildNumber, port);
		main = new Main(updateTargetGroup);
		result = main.parse();
		assertEquals(0, result);

	}

	@Test
	public void testInvokeViaCommandLineDeployWithFileAndSNS() {
		String[] args = CLIArgBuilder.createSimpleStackWithSNS(testName);
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsimpleStack");
		assertEquals(0,result);
	}

	@Test
	public void testInokeViaCommandLineWithAutoPopulatedAvailabilityZones() {
		String[] args = CLIArgBuilder.createSubnetStackWithZones(testName);
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsimpleStackWithAZ");
		assertEquals(0, result);
	}
	
	@Test
	public void testUpdateViaCommandLineDeployWithFileAndSNS() {
		String[] create = CLIArgBuilder.createSubnetStack(testName); // no sns
		Main main = new Main(create);
		main.parse();
		
		String[] update = CLIArgBuilder.updateSimpleStack(testName, "-sns");
		main = new Main(update);
		int result = main.parse();
		
		deletesStacks.ifPresent("CfnAssistTestsubnet");
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWithFilePassedInParam() {
		String[] args = CLIArgBuilder.createSubnetStackWithParams(testName);
		Main main = new Main(args);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssistTestsubnetWithParam");
		assertEquals(0,result);
	}
	
	@Test
	public void shouldListInstances() {
		String[] args = CLIArgBuilder.listInstances();
		Main main = new Main(args);
		int result = main.parse();
		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollback() throws CannotFindVpcException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenPurge(projAndEnv, "", FilesForTesting.ORDERED_SCRIPTS_FOLDER);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirAndThenRollbackWithSNS() throws CannotFindVpcException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenPurge(projAndEnv, "-sns", FilesForTesting.ORDERED_SCRIPTS_FOLDER);
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirDeltasAndThenRollback() throws CannotFindVpcException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenPurge(projAndEnv, "", FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString());
	}
	
	@Test
	public void testInvokeViaCommandLineDeployWholeDirDeltasAndThenRollbackWithSNS() throws CannotFindVpcException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);		
		invokeForDirAndThenPurge(projAndEnv, "-sns", FilesForTesting.ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.toString());
	}
	
	@Test
	public void testInvokeViaCommandLineAndThenStepBackWithSNS() throws CannotFindVpcException {
		ProjectAndEnv projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
		
		String[] argsDeploy = CLIArgBuilder.deployFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns", testName);
		Main main = new Main(argsDeploy);
		int result = main.parse();
		assertEquals(0,result, "deploy failed");
		
		String[] stepback = CLIArgBuilder.back("-sns");
		
		// step back first stack
		main = new Main(stepback);
		int resultA = main.parse();
		// step back second stack
		main = new Main(stepback);
		int resultB = main.parse();
		
		vpcRepository.initAllTags(altEnvVPC.vpcId(), altProjectAndEnv);
		
		assertEquals(0,resultA, "first back failed");
		assertEquals(0,resultB, "second back failed");
	}

	private void invokeForDirAndThenPurge(ProjectAndEnv projAndEnv,
										  String sns, String orderedScriptsFolder) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
		
		String[] argsDeploy = CLIArgBuilder.deployFromDir(orderedScriptsFolder, sns, testName);
		Main main = new Main(argsDeploy);
		int result = main.parse();
		assertEquals(0,result, "deploy failed");
		
		String[] rollbackDeploy = CLIArgBuilder.purge(sns);
		main = new Main(rollbackDeploy);
		result = main.parse();
		
		//clean up as needed
		vpcRepository.initAllTags(altEnvVPC.vpcId(), altProjectAndEnv);
		//cfnClient.setRegions(EnvironmentSetupForTests.getRegion());
		
		// check
		assertEquals(0,result, "purge failed");
	}
	
	@Disabled("cant find way to label at existing stack via apis")
	@Test
	public void testInvokeViaCommandLineTagExistingStack() throws IOException {
		EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, altEnvVPC.vpcId(),"");
		
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

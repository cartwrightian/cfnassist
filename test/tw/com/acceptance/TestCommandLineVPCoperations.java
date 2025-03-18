package tw.com.acceptance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Vpc;
import tw.com.CLIArgBuilder;
import tw.com.EnvironmentSetupForTests;
import tw.com.commandline.Main;
import tw.com.providers.CloudClient;
import tw.com.repository.VpcRepository;

import static org.junit.Assert.assertEquals;

public class TestCommandLineVPCoperations {
	
	private static Ec2Client ec2Client;
	private Vpc altEnvVPC;
	
	@BeforeEach
	public void beforeEveryTestRun() {
		VpcRepository vpcRepository = new VpcRepository(new CloudClient(ec2Client, new DefaultAwsRegionProviderChain()));
		altEnvVPC = EnvironmentSetupForTests.findAltVpc(vpcRepository);	
	}

	@BeforeAll
	public static void beforeAllTestsOnce() {
		ec2Client = EnvironmentSetupForTests.createEC2Client();
	}

	@Test
	public void testInvokeInitViaCommandLine() throws InterruptedException {	
		EnvironmentSetupForTests.clearVpcTags(ec2Client, altEnvVPC);
		String[] args = CLIArgBuilder.initVPC(EnvironmentSetupForTests.ALT_ENV, EnvironmentSetupForTests.PROJECT, altEnvVPC.vpcId());

		Main main = new Main(args);
		int result = main.parse();

		assertEquals(0,result);
	}
	
	@Test
	public void testInvokeResetViaCommandLine() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-reset"
				};
		Main main = new Main(args);
		assertEquals(0,main.parse());
	}

	@Test
	public void testTagVPCViaCommandLine() {
        String[] args = CLIArgBuilder.tagVPC("TEST_TAG_NAME", "Test Tag Value");
		Main main = new Main(args);
		assertEquals(0,main.parse());
	}

}

package tw.com.acceptance;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.commandline.Main;
import tw.com.providers.CloudClient;
import tw.com.repository.VpcRepository;

import static org.junit.Assert.assertEquals;

public class TestCommandLineVLCoperations {
	
	private static AmazonEC2Client ec2Client;
	private Vpc altEnvVPC;
	
	@Before
	public void beforeEveryTestRun() {
		VpcRepository vpcRepository = new VpcRepository(new CloudClient(ec2Client));
		altEnvVPC = EnvironmentSetupForTests.findAltVpc(vpcRepository);	
	}

	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
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

}

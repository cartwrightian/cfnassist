package tw.com.acceptance;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.EnvironmentSetupForTests;
import tw.com.commandline.Main;
import tw.com.repository.VpcRepository;

public class TestCommandLineVLCoperations {
	
	private static AmazonEC2Client ec2Client;
	private VpcRepository vpcRepository;
	private Vpc altEnvVPC;
	
	@Before
	public void beforeEveryTestRun() {
		vpcRepository = new VpcRepository(ec2Client);		
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
	
	@Test
	public void testInvokeResetViaCommandLineWithExtraParams() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-reset",
				"-parameters", "testA=123;testB=123"
				};
		Main main = new Main(args);
		assertEquals(0,main.parse());
	}

}

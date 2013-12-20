package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class TestParameterInjection {
	private static final String ACL_FILENAME = "src/cfnScripts/acl.json";

	private static AwsProvider aws;
	private static VpcRepository vpcRepository;
	private static String subnetStackName;
	private static String env = TestAwsFacade.ENV;
	private static String proj = TestAwsFacade.PROJECT;
	
	@BeforeClass
	public static void beforeAllTestsRun() throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, TestAwsFacade.getRegion());
		vpcRepository = new VpcRepository(credentialsProvider, TestAwsFacade.getRegion());
		
		subnetStackName = aws.applyTemplate(new File(TestAwsFacade.SUBNET_FILENAME), proj, env);
		String status = aws.waitForCreateFinished(subnetStackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
	}
	
	@AfterClass 
	public static void afterAllTestsHaveRun() throws WrongNumberOfStacksException, InterruptedException {
		TestAwsFacade.validatedDelete(subnetStackName, aws);
	}

	@Test
	public void shouldBeAbleToFetchValuesForParameters() throws FileNotFoundException, IOException, InvalidParameterException {
		Vpc vpc = vpcRepository.findVpcForEnv(proj, env);
		
		EnvironmentTag envTag = new EnvironmentTag(env);
		List<Parameter> result = aws.fetchAutopopulateParametersFor(new File(ACL_FILENAME), envTag);
		
		assertEquals(1, result.size());
		
		Parameter expectedParam = result.get(0);
		assertEquals("subnet", expectedParam.getParameterKey());
		
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AmazonEC2Client ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider); 
		
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		
		assertEquals(1,subnets.size());
		Subnet testSubnet = subnets.get(0);
		String subnetPhysicalId = testSubnet.getSubnetId();
		
		assertEquals(subnetPhysicalId, expectedParam.getParameterValue());
	}
	
	@Test
	public void autoInjectParameterTemplate() throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {			
		String aclStackName = aws.applyTemplate(new File(ACL_FILENAME), proj, env);	
		
		String status = aws.waitForCreateFinished(aclStackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		TestAwsFacade.validatedDelete(aclStackName, aws);		
	}
	

}

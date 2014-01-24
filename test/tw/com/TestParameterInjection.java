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
	private static String env = EnvironmentSetupForTests.ENV;
	private static String proj = EnvironmentSetupForTests.PROJECT;
	private static ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj, env);

	
	@BeforeClass
	public static void beforeAllTestsRun() throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
		vpcRepository = new VpcRepository(EnvironmentSetupForTests.createEC2Client(credentialsProvider));
		
		subnetStackName = aws.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME), mainProjectAndEnv);
		String status = aws.waitForCreateFinished(subnetStackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
	}
	
	@AfterClass 
	public static void afterAllTestsHaveRun() throws WrongNumberOfStacksException, InterruptedException {
		EnvironmentSetupForTests.validatedDelete(subnetStackName, aws);
	}

	@Test
	public void shouldBeAbleToFetchValuesForParameters() throws FileNotFoundException, IOException, InvalidParameterException {
		Vpc vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		
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
	public void autoInjectParameterTemplate() throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {			
		String aclStackName = aws.applyTemplate(new File(ACL_FILENAME), mainProjectAndEnv);	
		
		String status = aws.waitForCreateFinished(aclStackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		EnvironmentSetupForTests.validatedDelete(aclStackName, aws);		
	}
	

}

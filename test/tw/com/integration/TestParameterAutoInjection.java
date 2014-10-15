package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.repository.CfnRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class TestParameterAutoInjection {
	
	private static AwsFacade aws;
	private static VpcRepository vpcRepository;
	private static StackNameAndId subnetStackName;
	private static String env = EnvironmentSetupForTests.ENV;
	private static String proj = EnvironmentSetupForTests.PROJECT;
	private static ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj, env);

	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	
	@BeforeClass
	public static void beforeAllTestsOnce() throws FileNotFoundException, CfnAssistException, IOException, InvalidParameterException, InterruptedException {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
		vpcRepository = new VpcRepository(ec2Client);
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository);
		aws = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		
		subnetStackName = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), mainProjectAndEnv);
	}
	
	@Rule public TestName test = new TestName();
	
	@Before
	public void beforeEachTestRuns() {
		aws.setCommentTag(test.getMethodName());
	}
	
	@AfterClass 
	public static void afterAllTestsHaveRun() throws CfnAssistException, InterruptedException {
		new DeletesStacks(cfnClient).ifPresent(subnetStackName).act();
	}

	@Test
	public void shouldBeAbleToFetchValuesForParameters() throws FileNotFoundException, IOException, InvalidParameterException, CannotFindVpcException {
		Vpc vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		
		File file = new File(FilesForTesting.ACL);
		List<TemplateParameter> declaredParameters = aws.validateTemplate(file);
		List<Parameter> result = aws.fetchAutopopulateParametersFor(file, mainProjectAndEnv, declaredParameters);
		
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

}

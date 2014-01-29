package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.AmazonEC2Client;

public class TestAwsFacade {

	private AWSCredentialsProvider credentialsProvider;
	private AwsProvider aws;
	private ProjectAndEnv projectAndEnv;
	private MonitorStackEvents monitor;
	
	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		AmazonEC2Client ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new PollingStackMonitor(cfnRepository);	
		aws = new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository);
		projectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	}

	@Test
	public void testReturnCorrectParametersFromValidation() throws FileNotFoundException, IOException {
		List<TemplateParameter> result = aws.validateTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME));
		
		assertEquals(4, result.size());
		
		int i = 0;
		for(i=0; i<4; i++) {
			TemplateParameter parameter = result.get(i);
			if (parameter.getParameterKey().equals("zoneA")) break;		
		}
		TemplateParameter zoneAParameter = result.get(i);
		
		assertEquals("zoneA", zoneAParameter.getParameterKey());
		assertEquals("eu-west-1a", zoneAParameter.getDefaultValue());
		assertEquals("zoneADescription", zoneAParameter.getDescription());
	}
	
	@Test
	public void createStacknameFromEnvAndFile() {
		String stackName = aws.createStackName(new File(EnvironmentSetupForTests.SUBNET_FILENAME),projectAndEnv);
		assertEquals("CfnAssistTestsubnet", stackName);
	}
	
	@Test 
	public void shouldIncludeBuildNumberWhenFormingStackname() {
		projectAndEnv.addBuildNumber("042");
		String stackName = aws.createStackName(new File(EnvironmentSetupForTests.SUBNET_FILENAME),projectAndEnv);
		
		assertEquals("CfnAssist042Testsubnet", stackName);
	}
	
	@Test
	public void createsAndDeleteSubnetFromTemplate() throws FileNotFoundException, IOException, WrongNumberOfStacksException, 
		InterruptedException, InvalidParameterException, StackCreateFailed {
		String stackName = aws.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME), projectAndEnv);	
		
		EnvironmentSetupForTests.validatedDelete(stackName, aws);
	}

	@Test
	public void cannotAddEnvParameter() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		checkParameterCannotBePassed("env");
	}
	
	@Test
	public void cannotAddvpcParameter() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		checkParameterCannotBePassed("vpc");
	}
	
	@Test
	public void cannotAddbuildParameter() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		checkParameterCannotBePassed("build");
	}

	private void checkParameterCannotBePassed(String parameterName)
			throws FileNotFoundException, IOException,
			WrongNumberOfStacksException, InterruptedException,
			StackCreateFailed {
		Parameter parameter = new Parameter();
		parameter.setParameterKey(parameterName);
		parameter.setParameterValue("test");
		
		Collection<Parameter> parameters = new HashSet<Parameter>();
		parameters.add(parameter);
		
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME), projectAndEnv, parameters);	
			fail("Should have thrown exception");
		}
		catch (InvalidParameterException exception) {
			// expected
		}
	}
	
}

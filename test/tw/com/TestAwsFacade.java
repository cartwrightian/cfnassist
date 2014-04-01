package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.AmazonEC2Client;

public class TestAwsFacade {

	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	
	private AwsProvider aws;
	private ProjectAndEnv projectAndEnv;
	private MonitorStackEvents monitor;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}
	
	@Rule public TestName testName = new TestName();
	
	@Before
	public void beforeTestsRun() {
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new PollingStackMonitor(cfnRepository);	
		aws = new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository);
		aws.setCommentTag(testName.getMethodName());
		projectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestsimpleStack");
	}

	@Test
	public void testReturnCorrectParametersFromValidation() throws FileNotFoundException, IOException {
		List<TemplateParameter> result = aws.validateTemplate(new File(EnvironmentSetupForTests.SUBNET_STACK_FILE));
		
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
		String stackName = aws.createStackName(new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE),projectAndEnv);
		assertEquals("CfnAssistTestsimpleStack", stackName);
	}
	
	@Test 
	public void shouldIncludeBuildNumberWhenFormingStackname() {
		projectAndEnv.addBuildNumber("042");
		String stackName = aws.createStackName(new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE),projectAndEnv);
		
		assertEquals("CfnAssist042TestsimpleStack", stackName);
	}
	
	@Test
	public void createsAndDeleteSimpleStackFromTemplate() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		StackId stackId = aws.applyTemplate(new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE), projectAndEnv);	
		
		EnvironmentSetupForTests.validatedDelete(stackId, aws);
	}
	
	@Test
	public void catchesStackAlreadyExistingAsExpected() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		StackId stackId = aws.applyTemplate(new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE), projectAndEnv);	
		
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE), projectAndEnv);
			fail("Should have thrown exception");
		} 
		catch(DuplicateStackException expected) {
			// expected
		}
		
		EnvironmentSetupForTests.validatedDelete(stackId, aws);
	}
	
	@Test
	public void handlesRollBackCompleteStatusAutomatically() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, IOException, InvalidParameterException, InterruptedException, WrongStackStatus, DuplicateStackException {

		StackId id = null;
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.CAUSEROLLBACK), projectAndEnv);
			fail("should have thrown");
		} catch (StackCreateFailed exception) {
			id = exception.getStackId();
		}	
		monitor.waitForRollbackComplete(id);
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.CAUSEROLLBACK), projectAndEnv);
			fail("should have thrown");
		} catch (StackCreateFailed exception) {
			// expected a create fail, and *not* a duplicate stack exception
			id = exception.getStackId();
		}	
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestcausesRollBack");

	}

	@Test
	public void cannotAddEnvParameter() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("env");
	}
	
	@Test
	public void cannotAddvpcParameter() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("vpc");
	}
	
	@Test
	public void cannotAddbuildParameter() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException {
		checkParameterCannotBePassed("build");
	}

	private void checkParameterCannotBePassed(String parameterName)
			throws FileNotFoundException, IOException,
			CfnAssistException, InterruptedException {
		Parameter parameter = new Parameter();
		parameter.setParameterKey(parameterName);
		parameter.setParameterValue("test");
		
		Collection<Parameter> parameters = new HashSet<Parameter>();
		parameters.add(parameter);
		
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE), projectAndEnv, parameters);	
			fail("Should have thrown exception");
		}
		catch (InvalidParameterException exception) {
			// expected
		}
	}
	
}

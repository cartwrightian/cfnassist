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

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

public class TestAwsFacade {

	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private AwsProvider aws;
	private ProjectAndEnv projectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	
	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
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
	public void createsAndDeleteSubnetFromTemplate() throws FileNotFoundException, IOException, WrongNumberOfStacksException, 
		InterruptedException, InvalidParameterException, StackCreateFailed {
		String stackName = aws.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME), projectAndEnv);	
		
		String status = aws.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		validatedDelete(stackName, aws);
	}

	public static void validatedDelete(String stackName, AwsProvider provider)
			throws WrongNumberOfStacksException, InterruptedException {
		provider.deleteStack(stackName);
		String status = provider.waitForDeleteFinished(stackName);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
	}
	
	@Test
	public void cannotAddEnvParameter() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		Collection<Parameter> parameters = new HashSet<Parameter>();
		
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey("env");
		envParameter.setParameterValue("test");
		parameters.add(envParameter);
		
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME), projectAndEnv , parameters);	
			fail("Should have thrown exception");
		}
		catch (InvalidParameterException exception) {
			// expected
		}
	}
	
	@Test
	public void cannotAddvpcParameter() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		Collection<Parameter> parameters = new HashSet<Parameter>();
		
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey("vpc");
		envParameter.setParameterValue("test");
		parameters.add(envParameter);
		
		try {
			aws.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME), projectAndEnv, parameters);	
			fail("Should have thrown exception");
		}
		catch (InvalidParameterException exception) {
			// expected
		}
	}
	
}

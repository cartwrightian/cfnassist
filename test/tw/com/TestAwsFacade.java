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

	private static final String SUBNET_FILENAME = "src/cfnScripts/subnet.json";
	private static final String ENV = "Test";
	private AwsProvider aws;

	@Before
	public void beforeTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider);
	}

	@Test
	public void testReturnCorrectParametersFromValidation() throws FileNotFoundException, IOException {
		List<TemplateParameter> result = aws.validateTemplate(new File(SUBNET_FILENAME));
		
		assertEquals(3, result.size());
		
		int i = 0;
		for(i=0; i<3; i++) {
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
		String stackName = aws.createStackName(new File(SUBNET_FILENAME),ENV);
		assertEquals("Testsubnet", stackName);
	}
	
	@Test
	public void createsSubnetFromTemplateAndParamters() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, InvalidParameterException {
		String stackName = aws.applyTemplate(new File(SUBNET_FILENAME), ENV);	
		
		String status = aws.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
	}
	
	@Test
	public void cannotAddenvParameter() throws FileNotFoundException, IOException {
		Collection<Parameter> parameters = new HashSet<Parameter>();
		
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey("env");
		envParameter.setParameterValue("test");
		parameters.add(envParameter);
		
		try {
			aws.applyTemplate(new File(SUBNET_FILENAME), ENV , parameters);	
			fail("Should have thrown exception");
		}
		catch (InvalidParameterException exception) {
			// expected
		}
	}
	
	@Test
	public void deleteStackByName() {
		String stackName = aws.createStackName(new File(SUBNET_FILENAME),ENV);
		aws.deleteStack(stackName);
	}
}

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

	private AwsFacade aws;

	@Before
	public void beforeTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider);
	}

	@Test
	public void testReturnCorrectParametersFromValidation() throws FileNotFoundException, IOException {
		List<TemplateParameter> result = aws.validateTemplate(new File("src/cfnScripts/subnet.json"));
		
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
	public void createsSubnetFromTemplateAndParamters() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException {
		Collection<Parameter> parameters = new HashSet<Parameter>();
		
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey("env");
		envParameter.setParameterValue("test");
		parameters.add(envParameter);
		
		String stackName = "TestAwsFacade";
		aws.applyTemplate(new File("src/cfnScripts/subnet.json"), stackName, parameters);	
		
		String status = aws.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
	}
	
	@Test
	public void deleteStackByName() {
		aws.deleteStack("TestAwsFacade");	
	}
}

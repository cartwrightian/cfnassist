package tw.com.unit;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.parameters.EnvVarParams;

public class TestEnvVarParams {
	
	private EnvVarParams envVarParams;
	private ProjectAndEnv projAndEnv;
	
	@Before
	public void beforeEveryTestRuns() {
		envVarParams = new EnvVarParams();
		projAndEnv = new ProjectAndEnv("cfnassist", "test");
	}
	
	///////
	// needs environmental variable set to testEnvVar set to testValue
	///////
	
	@Test
	public void shouldPickUpVariableFromEnvironmentIfDeclaredInTemplate() throws FileNotFoundException, CannotFindVpcException, IOException, InvalidParameterException {
		
		List<Parameter> results = new LinkedList<Parameter>();
		List<TemplateParameter> declaredParameters = new LinkedList<TemplateParameter>();
		declaredParameters.add(new TemplateParameter().
				withDescription("::ENV").
				withParameterKey("testEnvVar"));
		
		envVarParams.addParameters(results , declaredParameters, projAndEnv);
		
		assertEquals(1, results.size());
		Parameter firstResult = results.get(0);
		assertEquals("testValue", firstResult.getParameterValue());
		assertEquals("testEnvVar", firstResult.getParameterKey());	
	}
	
	@Test
	public void shouldThrowIfEnvVariableIsNotFound() throws FileNotFoundException, CannotFindVpcException, IOException {
	
		List<Parameter> results = new LinkedList<Parameter>();
		List<TemplateParameter> declaredParameters = new LinkedList<TemplateParameter>();
		declaredParameters.add(new TemplateParameter().
				withDescription("::ENV").
				withParameterKey("envVarShouldNotExist"));
		try {
			envVarParams.addParameters(results , declaredParameters, projAndEnv);
			fail("should have thrown");
		}
		catch(InvalidParameterException expected) {
			// expected
		}
	}

}

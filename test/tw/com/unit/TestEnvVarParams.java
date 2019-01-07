package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import org.junit.Before;
import org.junit.Test;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.parameters.EnvVarParams;
import tw.com.parameters.ProvidesZones;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestEnvVarParams implements ProvidesZones {
	
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
	public void shouldPickUpVariableFromEnvironmentIfDeclaredInTemplate() throws CannotFindVpcException, IOException, InvalidStackParameterException {
		
		List<Parameter> results = new LinkedList<>();
		List<TemplateParameter> declaredParameters = new LinkedList<>();
		declaredParameters.add(new TemplateParameter().
				withDescription("::ENV").
				withParameterKey("testEnvVar"));
		
		envVarParams.addParameters(results , declaredParameters, projAndEnv, this);
		
		assertEquals(1, results.size());
		Parameter firstResult = results.get(0);
		assertEquals("testValue", firstResult.getParameterValue());
		assertEquals("testEnvVar", firstResult.getParameterKey());	
	}
	
	@Test
	public void shouldThrowIfEnvVariableIsNotFound() throws CannotFindVpcException, IOException {
	
		List<Parameter> results = new LinkedList<>();
		List<TemplateParameter> declaredParameters = new LinkedList<>();
		declaredParameters.add(new TemplateParameter().
				withDescription("::ENV").
				withParameterKey("envVarShouldNotExist"));
		try {
			envVarParams.addParameters(results , declaredParameters, projAndEnv, this);
			fail("should have thrown");
		}
		catch(InvalidStackParameterException expected) {
			// expected
		}
	}

	@Override
	public Map<String, AvailabilityZone> getZones() {
		return null;
	}
}

package tw.com.parameters;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

public class EnvVarParams extends PopulatesParameters {
	private static final Logger logger = LoggerFactory.getLogger(EnvVarParams.class);

	@Override
	public void addParameters(Collection<Parameter> result,
			List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv)
			throws FileNotFoundException, CannotFindVpcException, IOException,
			InvalidStackParameterException {
		List<String> toPopulate = findParametersToFill(declaredParameters);
		populateFromEnv(result, toPopulate);
	}
	
	private List<String> findParametersToFill(List<TemplateParameter> declaredParameters) {
		List<String> results = new LinkedList<String>();
		for(TemplateParameter candidate : declaredParameters) {
			if (shouldPopulateFor(candidate)) {
				results.add(candidate.getParameterKey());
			}

		}
		return results;
	}
	
	private boolean shouldPopulateFor(TemplateParameter candidate) {
		if (candidate.getDescription()==null) {
			return false;
		}
		return candidate.getDescription().equals(PopulatesParameters.ENV_TAG);
	}

	private void populateFromEnv(Collection<Parameter> result,
			List<String> toPopulate) throws InvalidStackParameterException {
		for(String name : toPopulate) {
			logger.info("Attempt to populate parameters from environmental variable: " + name);
			String value = System.getenv(name);
			if (value==null) {
				logger.error("Environment variable not set, name was " + name);
				throw new InvalidStackParameterException(name);
			}
			result.add(new Parameter().withParameterKey(name).withParameterValue(value));
		}
		
	}


}

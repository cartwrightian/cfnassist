package tw.com.parameters;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

public abstract class PopulatesParameters {
	private static final Logger logger = LoggerFactory.getLogger(PopulatesParameters.class);

	abstract void addParameters(Collection<Parameter> result,
			List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv) throws FileNotFoundException, CannotFindVpcException, IOException, InvalidParameterException;
	
	public void addParameterTo(Collection<Parameter> parameters, List<TemplateParameter> declared, String parameterName, String parameterValue) {
		boolean isDeclared = false;
		for(TemplateParameter declaration : declared) {
			isDeclared = (declaration.getParameterKey().equals(parameterName));
			if (isDeclared==true) break;
		}
		if (!isDeclared) {
			logger.info(String.format("Not populating parameter %s as it is not declared in the json file", parameterName));
		} else {
			logger.info(String.format("Setting %s parameter to %s", parameterName, parameterValue));
			Parameter parameter = new Parameter();
			parameter.setParameterKey(parameterName);
			parameter.setParameterValue(parameterValue);
			parameters.add(parameter);
		}
	}

}

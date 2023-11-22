package tw.com.parameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public abstract class PopulatesParameters {
	private static final Logger logger = LoggerFactory.getLogger(PopulatesParameters.class);

	public static final String PARAMETER_ENV = "env";
	public static final String PARAMETER_VPC = "vpc";
	public static final String PARAMETER_BUILD_NUMBER = "build";

	public static final String PARAM_PREFIX = "::";
	public static final String CFN_TAG_ON_OUTPUT = PARAM_PREFIX+"CFN_TAG";
	public static final String ENV_TAG = PARAM_PREFIX+"ENV";
	public static final String CFN_TAG_ZONE = PARAM_PREFIX+"CFN_ZONE_";

	abstract void addParameters(Collection<Parameter> result,
								List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv, ProvidesZones providesZones) throws CannotFindVpcException, IOException, InvalidStackParameterException;
	
	protected void addParameterTo(Collection<Parameter> parameters, List<TemplateParameter> declared, String parameterName, String parameterValue) {
		boolean isDeclared = false;
		for(TemplateParameter declaration : declared) {
			isDeclared = (declaration.parameterKey().equals(parameterName));
			if (isDeclared) break;
		}
		if (!isDeclared) {
			logger.info(String.format("Not populating parameter %s as it is not declared in the json file", parameterName));
		} else {
			logger.info(String.format("Setting %s parameter to %s", parameterName, parameterValue));
			Parameter parameter = Parameter.builder().parameterKey(parameterName).parameterValue(parameterValue).build();
			parameters.add(parameter);
		}
	}

}

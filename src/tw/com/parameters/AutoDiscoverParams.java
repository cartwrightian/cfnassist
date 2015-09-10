package tw.com.parameters;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class AutoDiscoverParams extends PopulatesParameters {
	private static final Logger logger = LoggerFactory.getLogger(AutoDiscoverParams.class);

	private File templateFile;
	private VpcRepository vpcRepository;
	private CloudFormRepository cfnRepository;

	public AutoDiscoverParams(File file,VpcRepository vpcRepository,CloudFormRepository cfnRepository) {
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
		this.templateFile = file;
	}

	@Override
	public void addParameters(Collection<Parameter> result,
			List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv, ProvidesZones providesZones) throws CannotFindVpcException, IOException, InvalidStackParameterException {
		List<Parameter> autoPopulatedParametes = fetchAutopopulateParametersFor(templateFile, projAndEnv, declaredParameters);
		for (Parameter autoPop : autoPopulatedParametes) {
			result.add(autoPop);
		}	
	}
	
	private List<Parameter> fetchAutopopulateParametersFor(File file, ProjectAndEnv projectAndEnv, List<TemplateParameter> declaredParameters) throws IOException, InvalidStackParameterException, CannotFindVpcException {
		logger.info(String.format("Discover and populate parameters for %s and %s", file.getAbsolutePath(), projectAndEnv));
		List<Parameter> matches = new LinkedList<Parameter>();
		for(TemplateParameter templateParam : declaredParameters) {
			String name = templateParam.getParameterKey();
			if (isBuiltInParamater(name))
			{
				continue;
			}
			logger.info("Checking if parameter should be auto-populated from an existing resource, param name is " + name);
			String description = templateParam.getDescription();
			if (shouldPopulateFor(description)) {
				populateParameter(projectAndEnv, matches, name, description, declaredParameters);
			}
		}
		return matches;
	}
	
	private boolean isBuiltInParamater(String name) {
		boolean result = name.equals(PopulatesParameters.PARAMETER_ENV);
		if (result) {
			logger.info("Found built in parameter");
		}
		return result;
	}

	private boolean shouldPopulateFor(String description) {
		if (description==null) {
			return false;
		}
		return description.startsWith(PopulatesParameters.PARAM_PREFIX) && (!description.equals(PopulatesParameters.ENV_TAG));
	}
	
	private void populateParameter(ProjectAndEnv projectAndEnv, List<Parameter> matches, String parameterName, String parameterDescription, List<TemplateParameter> declaredParameters)
			throws InvalidStackParameterException, CannotFindVpcException {
		if (parameterDescription.equals(PopulatesParameters.CFN_TAG_ON_OUTPUT)) {
			populateParameterFromVPCTag(projectAndEnv, matches, parameterName, parameterDescription, declaredParameters);
		} else {
			populateParameterFromPhysicalID(projectAndEnv.getEnvTag(), matches, parameterName,
					parameterDescription, declaredParameters);
		}	
	}
	
	private void populateParameterFromVPCTag(ProjectAndEnv projectAndEnv,
			List<Parameter> matches, String parameterName,
			String parameterDescription,
			List<TemplateParameter> declaredParameters) throws CannotFindVpcException, InvalidStackParameterException {
		logger.info("Attempt to find VPC matching name: " + parameterName);
		String value = vpcRepository.getVpcTag(parameterName, projectAndEnv);
		if (value==null) {
			String msg = String.format("Failed to find VPC TAG to matching: %s", parameterName);
			logger.error(msg);
			throw new InvalidStackParameterException(msg);
		}
		addParameterTo(matches, declaredParameters, parameterName, value);		
	}

	private void populateParameterFromPhysicalID(EnvironmentTag envTag,
			List<Parameter> matches, String parameterName,
			String parameterDescription,
			List<TemplateParameter> declaredParameters)
			throws InvalidStackParameterException {
		String logicalId = parameterDescription.substring(PopulatesParameters.PARAM_PREFIX.length());
		logger.info("Attempt to find physical ID for LogicalID: " + logicalId);
		String value = cfnRepository.findPhysicalIdByLogicalId(envTag, logicalId);
		if (value==null) {
			String msg = String.format("Failed to find physicalID to match logicalID: %s required for parameter: %s" , logicalId, parameterName);
			logger.error(msg);
			throw new InvalidStackParameterException(msg);
		}
		logger.info(String.format("Found physicalID: %s matching logicalID: %s Populating this into parameter %s", value, logicalId, parameterName));
		addParameterTo(matches, declaredParameters, parameterName, value);
	}
	

}

package tw.com.parameters;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
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
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class AutoDiscoverParams extends PopulatesParameters {
	private static final Logger logger = LoggerFactory.getLogger(AutoDiscoverParams.class);

	private final File templateFile;
	private final VpcRepository vpcRepository;
	private final CloudFormRepository cfnRepository;

    public AutoDiscoverParams(File file,VpcRepository vpcRepository,CloudFormRepository cfnRepository) {
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
		this.templateFile = file;
	}

	@Override
	public void addParameters(Collection<Parameter> result,
							  List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv, ProvidesZones providesZones)
			throws CannotFindVpcException, IOException, InvalidStackParameterException {
        Map<String, AvailabilityZone> zones = providesZones.getZones();
        List<Parameter> autoPopulatedParametes = fetchAutopopulateParametersFor(projAndEnv, declaredParameters, zones);
        result.addAll(autoPopulatedParametes.stream().toList());
	}
	
	private List<Parameter> fetchAutopopulateParametersFor(ProjectAndEnv projectAndEnv, List<TemplateParameter> declaredParameters,
														   Map<String, AvailabilityZone> zones) throws IOException, InvalidStackParameterException, CannotFindVpcException {
		logger.info(format("Discover and populate parameters for %s and %s", templateFile.getAbsolutePath(), projectAndEnv));
		List<Parameter> matches = new LinkedList<>();
		for(TemplateParameter templateParam : declaredParameters) {
			String name = templateParam.parameterKey();
			if (isBuiltInParamater(name))
			{
				continue;
			}
			logger.info("Checking if parameter should be auto-populated from an existing resource, param name is " + name);
			String description = templateParam.description();
			if (shouldPopulateFor(description)) {
				populateParameter(projectAndEnv, matches, templateParam, declaredParameters, zones);
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
	
	private void populateParameter(ProjectAndEnv projectAndEnv, List<Parameter> results, TemplateParameter templateParameter,
								   List<TemplateParameter> declaredParameters, Map<String, AvailabilityZone> zones)
			throws InvalidStackParameterException, CannotFindVpcException {
		String parameterDescription = templateParameter.description();

		if (parameterDescription.equals(PopulatesParameters.CFN_TAG_ON_OUTPUT)) {
			populateParameterFromVPCTag(projectAndEnv, results, templateParameter, declaredParameters);
		} else if (parameterDescription.startsWith(PopulatesParameters.CFN_TAG_ZONE)) {
            populateParamForZone(results, declaredParameters, zones, templateParameter, parameterDescription);
		}
		else {
			populateParameterFromPhysicalID(projectAndEnv.getEnvTag(), results, templateParameter, declaredParameters);
		}	
	}

	private void populateParamForZone(Collection<Parameter> results,
									  List<TemplateParameter> declaredParameters,
									  Map<String, AvailabilityZone> zones, TemplateParameter templateParameter,
									  String parameterDescription) {
		String parameterName = templateParameter.parameterKey();

		logger.info(format("Check parameter for zone %s and target %s", parameterName, parameterDescription));
		String target = parameterDescription.replaceFirst(PopulatesParameters.CFN_TAG_ZONE, "").toLowerCase();
		logger.debug("Check for zone " + target);
		if (zones.containsKey(target)) {
			String zoneName = zones.get(target).zoneName();
			declaredParameters.stream().filter(declaredParameter -> declaredParameter.parameterKey().equals(parameterName)).
					forEach(declaredParameter -> {
						addParameterTo(results, declaredParameters, parameterName, zoneName);
						logger.info(format("Adding zone parameter %s with value %s", parameterName, zoneName));
					});
		} else {
			logger.error("Could not find matching zone for target " + target);
		}
	}

	private void populateParameterFromVPCTag(ProjectAndEnv projectAndEnv,
											 List<Parameter> results, TemplateParameter templateParameter,
											 List<TemplateParameter> declaredParameters) throws CannotFindVpcException, InvalidStackParameterException {
		String parameterName = templateParameter.parameterKey();

		logger.info("Attempt to find VPC matching name: " + parameterName);
		String value = vpcRepository.getVpcTag(parameterName, projectAndEnv);
		if (value==null) {
			String msg = format("Failed to find VPC TAG matching: %s", parameterName);
			logger.error(msg);
			throw new InvalidStackParameterException(msg);
		}
		addParameterTo(results, declaredParameters, parameterName, value);
	}

	private void populateParameterFromPhysicalID(EnvironmentTag envTag,
			List<Parameter> matches, TemplateParameter templateParameter,
			List<TemplateParameter> declaredParameters)
			throws InvalidStackParameterException {
		String parameterName = templateParameter.parameterKey();
		String parameterDescription = templateParameter.description();
		String logicalId = parameterDescription.substring(PopulatesParameters.PARAM_PREFIX.length());
		logger.info("Attempt to find physical ID for LogicalID: " + logicalId);
		String value = cfnRepository.findPhysicalIdByLogicalId(envTag, logicalId);
		if (value==null) {
			if (templateParameter.defaultValue()==null) {
				String msg = format("No default given, and Failed to find physicalID to match logicalID: %s required for parameter: %s", logicalId, parameterName);
				logger.error(msg);
				throw new InvalidStackParameterException(msg);
			}
			value = templateParameter.defaultValue();
			logger.info(format("Using default value %s for %s as did not find %s", value, parameterName, logicalId));
		} else {
			logger.info(format("Found physicalID: %s matching logicalID: %s Populating this into parameter %s", value, logicalId, parameterName));
		}
		addParameterTo(matches, declaredParameters, parameterName, value);
	}
	

}

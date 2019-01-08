package tw.com.parameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import tw.com.entity.ProjectAndEnv;

import java.util.Collection;
import java.util.List;

public class CfnBuiltInParams extends PopulatesParameters {
    private static final Logger logger = LoggerFactory.getLogger(CfnBuiltInParams.class);

    private String vpcId;

    public CfnBuiltInParams(String vpcId) {
		this.vpcId = vpcId;
	}

	@Override
	public void addParameters(Collection<Parameter> result, List<TemplateParameter> declaredParameters,
							  ProjectAndEnv projAndEnv, ProvidesZones zoneProvider) {
        logger.info("Populate built-in parameters");
		addParameterTo(result, declaredParameters, PopulatesParameters.PARAMETER_ENV, projAndEnv.getEnv());
		addParameterTo(result, declaredParameters, PopulatesParameters.PARAMETER_VPC, vpcId);
		if (projAndEnv.hasBuildNumber()) {
			addParameterTo(result, declaredParameters, PopulatesParameters.PARAMETER_BUILD_NUMBER, 
					projAndEnv.getBuildNumber().toString());
		}
    }

}

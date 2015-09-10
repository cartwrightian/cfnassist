package tw.com.parameters;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.ProjectAndEnv;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CfnBuiltInParams extends PopulatesParameters {
    private static final Logger logger = LoggerFactory.getLogger(CfnBuiltInParams.class);


    private String vpcId;
    private Map<String, AvailabilityZone> theZones = null;

    public CfnBuiltInParams(String vpcId) {
		this.vpcId = vpcId;
	}

	@Override
	public void addParameters(Collection<Parameter> result, List<TemplateParameter> declaredParameters,
							  ProjectAndEnv projAndEnv, ProvidesZones zoneProvider) {
		addParameterTo(result, declaredParameters, PopulatesParameters.PARAMETER_ENV, projAndEnv.getEnv());
		addParameterTo(result, declaredParameters, PopulatesParameters.PARAMETER_VPC, vpcId);
		if (projAndEnv.hasBuildNumber()) {
			addParameterTo(result, declaredParameters, PopulatesParameters.PARAMETER_BUILD_NUMBER, 
					projAndEnv.getBuildNumber().toString());
		}

        Map<String, AvailabilityZone> zones = getZones(zoneProvider);

        populateParamForZone(result, declaredParameters, zones, PopulatesParameters.ZONE_A,  "a");
        populateParamForZone(result, declaredParameters, zones, PopulatesParameters.ZONE_B,  "b");
        populateParamForZone(result, declaredParameters, zones, PopulatesParameters.ZONE_C, "c");

        //addParameterTo(result, declaredParameters, PopulatesParameters.ZONE_B, zones.get("b").getZoneName());
        //addParameterTo(result, declaredParameters, PopulatesParameters.ZONE_C, zones.get("c").getZoneName());

    }

    private void populateParamForZone(Collection<Parameter> result,
                                      List<TemplateParameter> declaredParameters,
                                      Map<String, AvailabilityZone> zones, String zone,
                                      String target) {
        logger.info(String.format("Check parameter for zone %s and target %s", zone, target));
        if (zones.containsKey(target)) {
            String zoneName = zones.get(target).getZoneName();
            declaredParameters.stream().filter(declaredParameter -> declaredParameter.getParameterKey().equals(zone)).
                    forEach(declaredParameter -> {
                        addParameterTo(result, declaredParameters, zone, zoneName);
                        logger.info(String.format("Adding zone parameter %s with value %s", zone, zoneName));
                    });
        } else {
            logger.error("Could not find matching zone for target " + target);
        }
    }

    private Map<String, AvailabilityZone> getZones(ProvidesZones zoneProvider) {
        if (theZones==null) {
            theZones = zoneProvider.getZones();
        }
        return theZones;
    }
}

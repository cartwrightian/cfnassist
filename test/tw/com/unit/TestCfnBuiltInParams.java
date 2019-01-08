package tw.com.unit;


import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.ProjectAndEnv;
import tw.com.parameters.CfnBuiltInParams;
import tw.com.parameters.ProvidesZones;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;


public class TestCfnBuiltInParams implements ProvidesZones {

    private CfnBuiltInParams parameters;
    private LinkedList<Parameter> results;
    private LinkedList<TemplateParameter> declaredParameters;
    private String vpcId;

    @Before
    public void beforeEachTestRuns() {

        vpcId = "vpcId";
        parameters = new CfnBuiltInParams(vpcId);

        results = new LinkedList<>();
        declaredParameters = new LinkedList<>();
    }

    @Test
    public void shouldNotPopulateEnvAndEpvParametersIfNotDeclared() {

        parameters.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);

        assertTrue(results.isEmpty());
    }

    @Test
    public void shouldPopulateEnvAndVPCIfDeclared() {
        declaredParameters.add(createParam("env"));
        declaredParameters.add(createParam("vpc"));

        ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        parameters.addParameters(results, declaredParameters, projAndEnv, this);

        assertEquals(2, results.size());
        assertTrue(isPresentIn(results, "env", projAndEnv.getEnv()));
        assertTrue(isPresentIn(results, "vpc", vpcId));

    }

    private TemplateParameter createParam(String key) {
        return TemplateParameter.builder().parameterKey(key).build();
    }

    @Test
    public void shouldPopulateEnvVpcAndBuildIfDeclared()  {
        declaredParameters.add(createParam("env"));
        declaredParameters.add(createParam("vpc"));
        declaredParameters.add(createParam("build"));

        ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        projAndEnv.addBuildNumber(5426);
        parameters.addParameters(results, declaredParameters, projAndEnv, this);

        assertEquals(3, results.size());
        assertTrue(isPresentIn(results, "env", projAndEnv.getEnv()));
        assertTrue(isPresentIn(results, "vpc", vpcId));
        assertTrue(isPresentIn(results, "build", "5426"));

    }

    @Test
    public void shouldPopulateEnvVpcIfDeclaredButBuildNotDeclared() {
        declaredParameters.add(createParam("env"));
        declaredParameters.add(createParam("vpc"));

        ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        projAndEnv.addBuildNumber(5426);
        parameters.addParameters(results, declaredParameters, projAndEnv, this);

        assertEquals(2, results.size());
        assertTrue(isPresentIn(results, "env", projAndEnv.getEnv()));
        assertTrue(isPresentIn(results, "vpc", vpcId));
    }

    private boolean isPresentIn(Collection<Parameter> results, String key, String value) {
        for(Parameter candidate : results) {
            if (candidate.parameterKey().equals(key) && (candidate.parameterValue().equals(value))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, AvailabilityZone> getZones() {
        return null;
    }
}

package tw.com.unit;


import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import org.junit.Before;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.parameters.CfnBuiltInParams;
import tw.com.parameters.ProvidesZones;

import java.io.IOException;
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
    public void shouldNotPopulateEnvAndEpvParametersIfNotDeclared() throws InvalidStackParameterException, CannotFindVpcException, IOException {

        parameters.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);

        assertTrue(results.isEmpty());
    }

    @Test
    public void shouldPopulateEnvAndVPCIfDeclared() throws InvalidStackParameterException, CannotFindVpcException, IOException {
        declaredParameters.add(new TemplateParameter().withParameterKey("env"));
        declaredParameters.add(new TemplateParameter().withParameterKey("vpc"));

        ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        parameters.addParameters(results, declaredParameters, projAndEnv, this);

        assertEquals(2, results.size());
        assertTrue(isPresentIn(results, "env", projAndEnv.getEnv()));
        assertTrue(isPresentIn(results, "vpc", vpcId));

    }

    @Test
    public void shouldPopulateEnvVpcAndBuildIfDeclared() throws InvalidStackParameterException, CannotFindVpcException, IOException {
        declaredParameters.add(new TemplateParameter().withParameterKey("env"));
        declaredParameters.add(new TemplateParameter().withParameterKey("vpc"));
        declaredParameters.add(new TemplateParameter().withParameterKey("build"));

        ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        projAndEnv.addBuildNumber(5426);
        parameters.addParameters(results, declaredParameters, projAndEnv, this);

        assertEquals(3, results.size());
        assertTrue(isPresentIn(results, "env", projAndEnv.getEnv()));
        assertTrue(isPresentIn(results, "vpc", vpcId));
        assertTrue(isPresentIn(results, "build", "5426"));

    }

    @Test
    public void shouldPopulateEnvVpcIfDeclaredButBuildNotDeclared() throws InvalidStackParameterException, CannotFindVpcException, IOException {
        declaredParameters.add(new TemplateParameter().withParameterKey("env"));
        declaredParameters.add(new TemplateParameter().withParameterKey("vpc"));

        ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        projAndEnv.addBuildNumber(5426);
        parameters.addParameters(results, declaredParameters, projAndEnv, this);

        assertEquals(2, results.size());
        assertTrue(isPresentIn(results, "env", projAndEnv.getEnv()));
        assertTrue(isPresentIn(results, "vpc", vpcId));
    }

    private boolean isPresentIn(Collection<Parameter> results, String key, String value) {
        for(Parameter candidate : results) {
            if (candidate.getParameterKey().equals(key) && (candidate.getParameterValue().equals(value))) {
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

package tw.com.unit;


import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.parameters.AutoDiscoverParams;
import tw.com.parameters.ProvidesZones;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TestAutoDiscoverParams extends EasyMockSupport implements ProvidesZones {
    private VpcRepository vpcRepository;
    private AutoDiscoverParams autoDiscover;
    private LinkedList<Parameter> results;
    private LinkedList<TemplateParameter> declaredParameters;

    @Before
    public void beforeEachTestRuns() {
        vpcRepository = createMock(VpcRepository.class);
        CloudFormRepository cfnRepository = createMock(CloudFormRepository.class);
        File templateFile = new File(FilesForTesting.SIMPLE_STACK_WITH_AZ);
        autoDiscover = new AutoDiscoverParams(templateFile, vpcRepository, cfnRepository);

        results = new LinkedList<>();
        declaredParameters = new LinkedList<>();
    }

    @Test
    public void shouldAddCorrectValueForTaggedParameter() throws IOException, CannotFindVpcException, InvalidStackParameterException {

        declaredParameters.add(TemplateParameter.builder().description(AutoDiscoverParams.CFN_TAG_ON_OUTPUT).parameterKey("paramKey").build());
        EasyMock.expect(vpcRepository.getVpcTag("paramKey", EnvironmentSetupForTests.getMainProjectAndEnv())).andReturn("tagValue");

        replayAll();
        autoDiscover.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);
        verifyAll();

        assertEquals(1, results.size());
        Parameter result = results.getFirst();
        assertEquals("paramKey", result.parameterKey());
        assertEquals("tagValue", result.parameterValue());
    }

    @Test
    public void shouldAddCorrectValueForZone() throws IOException, CannotFindVpcException, InvalidStackParameterException {

        declaredParameters.add(TemplateParameter.builder().description(AutoDiscoverParams.CFN_TAG_ZONE+"A").parameterKey("paramKey").build());

        replayAll();
        autoDiscover.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);
        verifyAll();

        assertEquals(1, results.size());
        Parameter result = results.getFirst();
        assertEquals("paramKey", result.parameterKey());
        assertEquals("aviailabilityZoneA", result.parameterValue());
    }

    @Override
    public Map<String, AvailabilityZone> getZones() {
        Map<String, AvailabilityZone> zones = new HashMap<>();
        zones.put("a", AvailabilityZone.builder().zoneName("aviailabilityZoneA").build());
        return zones;

    }
}

package tw.com.unit;


import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
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

class TestAutoDiscoverParams extends EasyMockSupport implements ProvidesZones {
    private VpcRepository vpcRepository;
    private AutoDiscoverParams autoDiscover;
    private LinkedList<Parameter> results;
    private LinkedList<TemplateParameter> declaredParameters;

    @BeforeEach
    public void beforeEachTestRuns() {
        vpcRepository = createMock(VpcRepository.class);
        CloudFormRepository cfnRepository = createMock(CloudFormRepository.class);
        File templateFile = new File(FilesForTesting.SIMPLE_STACK_WITH_AZ);
        autoDiscover = new AutoDiscoverParams(templateFile, vpcRepository, cfnRepository);

        results = new LinkedList<>();
        declaredParameters = new LinkedList<>();
    }

    @Test
    void shouldAddCorrectValueForTaggedParameter() throws IOException, CannotFindVpcException, InvalidStackParameterException {

        declaredParameters.add(TemplateParameter.builder().description(AutoDiscoverParams.CFN_TAG_ON_OUTPUT).parameterKey("paramKey").build());
        EasyMock.expect(vpcRepository.getVpcTag("paramKey", EnvironmentSetupForTests.getMainProjectAndEnv())).andReturn("tagValue");

        replayAll();
        autoDiscover.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);
        verifyAll();

        Assertions.assertEquals(1, results.size());
        Parameter result = results.getFirst();
        Assertions.assertEquals("paramKey", result.parameterKey());
        Assertions.assertEquals("tagValue", result.parameterValue());
    }

    @Test
    void shouldAddCorrectValueForZone() throws IOException, CannotFindVpcException, InvalidStackParameterException {

        declaredParameters.add(TemplateParameter.builder().description(AutoDiscoverParams.CFN_TAG_ZONE+"A").parameterKey("paramKey").build());

        replayAll();
        autoDiscover.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);
        verifyAll();

        Assertions.assertEquals(1, results.size());
        Parameter result = results.getFirst();
        Assertions.assertEquals("paramKey", result.parameterKey());
        Assertions.assertEquals("aviailabilityZoneA", result.parameterValue());
    }

    @Override
    public Map<String, AvailabilityZone> getZones() {
        Map<String, AvailabilityZone> zones = new HashMap<>();
        zones.put("a", AvailabilityZone.builder().zoneName("aviailabilityZoneA").build());
        return zones;

    }
}

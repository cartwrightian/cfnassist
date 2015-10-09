package tw.com.unit;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.*;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CloudFormationClient;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@RunWith(EasyMockRunner.class)
public class TestCloudFormationClient extends EasyMockSupport {

    private CloudFormationClient client;
    private AmazonCloudFormationClient cfnClient;

    @Before
    public void beforeEachTestRuns() {
        cfnClient = createMock(AmazonCloudFormationClient.class);
        client = new CloudFormationClient(cfnClient);
    }

    @Test
    public void shouldDescribeStack() throws WrongNumberOfStacksException {

        DescribeStacksRequest request = new DescribeStacksRequest().withStackName("stackName");
        Stack stack = new Stack();
        DescribeStacksResult answer = new DescribeStacksResult().withStacks(stack);
        EasyMock.expect(cfnClient.describeStacks(request)).andReturn(answer);

        replayAll();
        Stack result = client.describeStack("stackName");
        verifyAll();
        assertEquals(result, stack);
    }

    @Test
    public void shouldDescribesAllStacks() {

        Stack stackA = new Stack();
        Stack stackB = new Stack();
        DescribeStacksResult answer = new DescribeStacksResult().withStacks(stackA, stackB);
        EasyMock.expect(cfnClient.describeStacks()).andReturn(answer);

        replayAll();
        List<Stack> result = client.describeAllStacks();
        verifyAll();
        assertEquals(2, result.size());
        assertTrue(result.contains(stackA));
        assertTrue(result.contains(stackB));
    }

    @Test
    public void shouldCreateStack() throws CfnAssistException {
        Collection<Tag> tags = new LinkedList<>();
        tags.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_PROJECT", "CfnAssist"));
        tags.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_ENV", "Test"));
        tags.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_COMMENT", "commentForStack"));

        Collection<Parameter> parameters = new LinkedList<>();
        Parameter parameter = new Parameter().withParameterKey("paramKey").withParameterValue("paramValue");
        parameters.add(parameter);
        MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
        monitor.addMonitoringTo(EasyMock.isA(CreateStackRequest.class));
        EasyMock.expectLastCall();

        CreateStackRequest createStackRequest = new CreateStackRequest().withStackName("stackName").
                withTemplateBody("{json}").withTags(tags).withParameters(parameters);
        CreateStackResult createStackResponse = new CreateStackResult().withStackId("stackId");
        EasyMock.expect(cfnClient.createStack(createStackRequest)).andReturn(createStackResponse);

        replayAll();
        StackNameAndId result = client.createStack(EnvironmentSetupForTests.getMainProjectAndEnv(), "{json}", "stackName", parameters, monitor, "commentForStack");
        verifyAll();

        assertEquals("stackName", result.getStackName());
        assertEquals("stackId", result.getStackId());
    }

    @Test
    public void shouldDeleteStack() {

        DeleteStackRequest deleteRequest = new DeleteStackRequest().withStackName("stackName");
        cfnClient.deleteStack(deleteRequest);
        EasyMock.expectLastCall();


        replayAll();
        client.deleteStack("stackName");
        verifyAll();
    }

    @Test
    public void shouldGetStackEvents() {

        DescribeStackEventsRequest eventRequest = new DescribeStackEventsRequest().withStackName("stackName");
        StackEvent eventA = new StackEvent();
        StackEvent eventB = new StackEvent();
        DescribeStackEventsResult eventResponse = new DescribeStackEventsResult().withStackEvents(eventA, eventB);
        EasyMock.expect(cfnClient.describeStackEvents(eventRequest)).andReturn(eventResponse);

        replayAll();
        List<StackEvent> result = client.describeStackEvents("stackName");
        verifyAll();

        assertEquals(2, result.size());
        assertTrue(result.contains(eventA));
        assertTrue(result.contains(eventB));
    }

    public void shouldGetStackResources() {
        DescribeStackResourcesRequest request = new DescribeStackResourcesRequest().withStackName("stackName");
        StackResource resA = new StackResource();
        StackResource resB = new StackResource();
        DescribeStackResourcesResult response = new DescribeStackResourcesResult().withStackResources(resA, resB);

        EasyMock.expect(cfnClient.describeStackResources(request)).andReturn(response);
        replayAll();
        List<StackResource> result = client.describeStackResources("stackName");
        verifyAll();

        assertEquals(2, result.size());
        assertTrue(result.contains(resA));
        assertTrue(result.contains(resB));
    }

    @Test
    public void shouldUpdateStack() throws NotReadyException {
        Collection<Parameter> parameters = new LinkedList<>();
        Parameter parameter = new Parameter().withParameterKey("paramKey").withParameterValue("paramValue");
        parameters.add(parameter);

        MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
        monitor.addMonitoringTo(EasyMock.isA(UpdateStackRequest.class));
        EasyMock.expectLastCall();

        UpdateStackRequest request = new UpdateStackRequest().withStackName("stackName").
                withParameters(parameters).withTemplateBody("{json}");
        UpdateStackResult response = new UpdateStackResult().withStackId("stackId");
        EasyMock.expect(cfnClient.updateStack(request)).andReturn(response);

        replayAll();
        StackNameAndId result = client.updateStack("{json}", parameters, monitor, "stackName");
        verifyAll();

        assertEquals("stackName", result.getStackName());
        assertEquals("stackId", result.getStackId());
    }

    @Test
    public void shouldValidateTemplate() {
        Collection<TemplateParameter> parameters = new LinkedList<>();
        TemplateParameter parameter = new TemplateParameter().withParameterKey("paramKey");
        parameters.add(parameter);

        ValidateTemplateRequest request =new ValidateTemplateRequest().withTemplateBody("{json}");
        ValidateTemplateResult response = new ValidateTemplateResult().withParameters(parameters);
        EasyMock.expect(cfnClient.validateTemplate(request)).andReturn(response);

        replayAll();
        List<TemplateParameter> result = client.validateTemplate("{json}");
        verifyAll();

        assertEquals(1, result.size());
        assertTrue(result.contains(parameter));


    }

}

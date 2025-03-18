package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.*;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.entity.StackNameAndId;
import tw.com.entity.Tagging;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CFNClient;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

class TestCFNClient extends EasyMockSupport {

    private CFNClient client;
    private software.amazon.awssdk.services.cloudformation.CloudFormationClient cfnClient;

    @BeforeEach
    public void beforeEachTestRuns() {
        cfnClient = createMock(software.amazon.awssdk.services.cloudformation.CloudFormationClient.class);
        client = new CFNClient(cfnClient);
    }

    @Test
    void shouldDescribeStack() throws WrongNumberOfStacksException {

        DescribeStacksRequest request = DescribeStacksRequest.builder().stackName("stackName").build();
        Stack stack = Stack.builder().build();

        DescribeStacksResponse answer = DescribeStacksResponse.builder().stacks(stack).build();
        EasyMock.expect(cfnClient.describeStacks(request)).andReturn(answer);

        replayAll();
        Stack result = client.describeStack("stackName");
        verifyAll();
        Assertions.assertEquals(result, stack);
    }

    @Test
    void shouldDescribesAllStacks() {

        Stack stackA = Stack.builder().build();
        Stack stackB = Stack.builder().build();
        DescribeStacksResponse answer = DescribeStacksResponse.builder().stacks(stackA, stackB).build();
        EasyMock.expect(cfnClient.describeStacks()).andReturn(answer);

        replayAll();
        List<Stack> result = client.describeAllStacks();
        verifyAll();
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.contains(stackA));
        Assertions.assertTrue(result.contains(stackB));
    }

    @Test
    void shouldCreateStack() throws CfnAssistException {
        Collection<Tag> tags = new LinkedList<>();
        tags.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_PROJECT", "CfnAssist"));
        tags.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_ENV", "Test"));
        tags.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_COMMENT", "commentForStack"));

        Collection<Parameter> parameters = new LinkedList<>();
        Parameter parameter = Parameter.builder().parameterKey("paramKey").parameterKey("paramValue").build();
        parameters.add(parameter);
        MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
        monitor.addMonitoringTo(EasyMock.isA(CreateStackRequest.Builder.class));
        EasyMock.expectLastCall();

        CreateStackRequest createStackRequest = CreateStackRequest.builder().stackName("stackName").
                templateBody("{json}").tags(tags).parameters(parameters).build();
        CreateStackResponse createStackResponse = CreateStackResponse.builder().stackId("stackId").build();
        EasyMock.expect(cfnClient.createStack(createStackRequest)).andReturn(createStackResponse);

        replayAll();
        Tagging tagging = new Tagging();
        tagging.setCommentTag("commentForStack");
        StackNameAndId result = client.createStack(EnvironmentSetupForTests.getMainProjectAndEnv(), "{json}",
                "stackName", parameters, monitor, tagging);
        verifyAll();

        Assertions.assertEquals("stackName", result.getStackName());
        Assertions.assertEquals("stackId", result.getStackId());
    }

    @Test
    void shouldDeleteStack() {

        DeleteStackRequest deleteRequest = DeleteStackRequest.builder().stackName("stackName").build();
        DeleteStackResponse result = DeleteStackResponse.builder().build();
        EasyMock.expect(cfnClient.deleteStack(deleteRequest)).andReturn(result);

        replayAll();
        client.deleteStack("stackName");
        verifyAll();
    }

    @Test
    void shouldGetStackEvents() {

        DescribeStackEventsRequest eventRequest = DescribeStackEventsRequest.builder().stackName("stackName").build();
        StackEvent eventA = StackEvent.builder().build();
        StackEvent eventB = StackEvent.builder().build();
        DescribeStackEventsResponse eventResponse = DescribeStackEventsResponse.builder().
                stackEvents(eventA, eventB).build();
        EasyMock.expect(cfnClient.describeStackEvents(eventRequest)).andReturn(eventResponse);

        replayAll();
        List<StackEvent> result = client.describeStackEvents("stackName");
        verifyAll();

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.contains(eventA));
        Assertions.assertTrue(result.contains(eventB));
    }

    public void shouldGetStackResources() {
        DescribeStackResourcesRequest request = DescribeStackResourcesRequest.builder().stackName("stackName").build();
        StackResource resA = StackResource.builder().build();
        StackResource resB = StackResource.builder().build();
        DescribeStackResourcesResponse response = DescribeStackResourcesResponse.builder().stackResources(resA, resB).build();

        EasyMock.expect(cfnClient.describeStackResources(request)).andReturn(response);
        replayAll();
        List<StackResource> result = client.describeStackResources("stackName");
        verifyAll();

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.contains(resA));
        Assertions.assertTrue(result.contains(resB));
    }

    private ListStacksRequest listActionStackRequest() {
        return ListStacksRequest.builder().stackStatusFilters(
                StackStatus.CREATE_COMPLETE,
                StackStatus.ROLLBACK_COMPLETE,
                StackStatus.UPDATE_COMPLETE,
                StackStatus.UPDATE_ROLLBACK_COMPLETE,

                StackStatus.CREATE_IN_PROGRESS,
                StackStatus.UPDATE_IN_PROGRESS,
                StackStatus.ROLLBACK_IN_PROGRESS,
                StackStatus.UPDATE_ROLLBACK_IN_PROGRESS,
                StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS,

                StackStatus.CREATE_FAILED,
                StackStatus.DELETE_FAILED,
                StackStatus.ROLLBACK_FAILED,
                StackStatus.UPDATE_ROLLBACK_FAILED).build();
    }

    @Test
    void shouldTestStackExists() {

        ListStacksRequest listStackRequest = listActionStackRequest();
        StackSummary stackSummary = StackSummary.builder().
                stackName("stackName").
                stackStatus(StackStatus.CREATE_COMPLETE).build();
        ListStacksResponse summary = ListStacksResponse.builder().stackSummaries(stackSummary).build();
        EasyMock.expect(cfnClient.listStacks(listStackRequest)).andReturn(summary);

        replayAll();
        Assertions.assertTrue(client.stackExists("stackName"));
        verifyAll();
    }

    @Test
    void shouldTestStackNotExists() {
        ListStacksRequest listStackRequest = listActionStackRequest();
        ListStacksResponse summary = ListStacksResponse.builder().build();
        EasyMock.expect(cfnClient.listStacks(listStackRequest)).andReturn(summary);

        replayAll();
        Assertions.assertFalse(client.stackExists("stackName"));
        verifyAll();
    }

    @Test
    void shouldUpdateStack() throws NotReadyException {
        Collection<Parameter> parameters = new LinkedList<>();
        Parameter parameter = Parameter.builder().parameterKey("paramKey").parameterValue("paramValue").build();
        parameters.add(parameter);

        MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
        monitor.addMonitoringTo(EasyMock.isA(UpdateStackRequest.Builder.class));
        EasyMock.expectLastCall();

        UpdateStackRequest request = UpdateStackRequest.builder().
                stackName("stackName").
                parameters(parameters).
                templateBody("{json}").build();

        UpdateStackResponse response = UpdateStackResponse.builder().stackId("stackId").build();
        EasyMock.expect(cfnClient.updateStack(request)).andReturn(response);

        replayAll();
        StackNameAndId result = client.updateStack("{json}", parameters, monitor, "stackName");
        verifyAll();

        Assertions.assertEquals("stackName", result.getStackName());
        Assertions.assertEquals("stackId", result.getStackId());
    }

    @Test
    void shouldValidateTemplate() {
        Collection<TemplateParameter> parameters = new LinkedList<>();
        TemplateParameter parameter = TemplateParameter.builder().parameterKey("paramKey").build();
        parameters.add(parameter);

        ValidateTemplateRequest request = ValidateTemplateRequest.builder().templateBody("{json}").build();
        ValidateTemplateResponse response = ValidateTemplateResponse.builder().parameters(parameters).build();
        EasyMock.expect(cfnClient.validateTemplate(request)).andReturn(response);

        replayAll();
        List<TemplateParameter> result = client.validateTemplate("{json}");
        verifyAll();

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.contains(parameter));


    }

}

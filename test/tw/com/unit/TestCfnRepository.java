package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import tw.com.*;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CFNClient;
import tw.com.repository.CfnRepository;
import tw.com.repository.CloudRepository;

import java.util.*;

class TestCfnRepository extends EasyMockSupport {
	
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);

    private CFNClient formationClient;
	private CfnRepository repository;
	private EnvironmentTag envTag;
	private Integer noBuildNumber = -1;

	private CloudRepository cloudRepository;
	
	@BeforeEach
	public void beforeEachTestRuns() {
		formationClient = createMock(CFNClient.class);
		
		cloudRepository = createMock(CloudRepository.class);
		repository = new CfnRepository(formationClient, cloudRepository, mainProjectAndEnv.getProject());	
		envTag = new EnvironmentTag(EnvironmentSetupForTests.ENV);
	}

	@Test
    void testGetAllStacks() {
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist");
		List<Stack> list = new LinkedList<>();
		list.add(Stack.builder().tags(tags).stackName("matchingStack").build());
		list.add(Stack.builder().stackName("noMatchingTags").build());
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);

		replayAll();
		List<StackEntry> results = repository.getStacks();
		Assertions.assertEquals(1, results.size());
		results = repository.getStacks(); // second call should hit cache
		Assertions.assertEquals(1, results.size());

		verifyAll();	
	}

	@Test
    void shouldGetDrifts() {
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist");

		List<Stack> stacks = new LinkedList<>();
		stacks.add(Stack.builder().stackName("stackName").tags(tags).build());
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks);

		String refID = "someRefId";
		EasyMock.expect(formationClient.detectDrift("stackName")).andReturn(refID);
		EasyMock.expect(formationClient.driftDetectionInProgress(refID)).andReturn(true);
		EasyMock.expect(formationClient.driftDetectionInProgress(refID)).andReturn(true);
		EasyMock.expect(formationClient.driftDetectionInProgress(refID)).andReturn(false);
		EasyMock.expect(formationClient.getDriftDetectionResult("stackName",refID)).andReturn(
				new CFNClient.DriftStatus("stackName", StackDriftStatus.DRIFTED, 42));

		replayAll();
		List<StackEntry> results = repository.getStackDrifts(mainProjectAndEnv);
		Assertions.assertEquals(1, results.size());
		CFNClient.DriftStatus driftStatus = results.get(0).getDriftStatus();
		Assertions.assertEquals(42, driftStatus.getDriftedStackResourceCount());
		Assertions.assertEquals(StackDriftStatus.DRIFTED, driftStatus.getStackDriftStatus());
		verifyAll();
	}
	
	@Test
    void testGetStacksByEnv() {
		List<Tag> tagsA = EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist");
		List<Tag> tagsB = new LinkedList<>();
		tagsB.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_ENV", "WrongTest"));
		tagsB.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_PROJECT", "CfnAssist"));
		
		List<Stack> list = new LinkedList<>();
		list.add(Stack.builder().tags(tagsA).stackName("matchingStack").build());
		list.add(createStackWithTags("wrongEnvStack", tagsB));
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);

		replayAll();
		List<StackEntry> results = repository.getStacks(envTag);
		Assertions.assertEquals(1, results.size());
		results = repository.getStacks(envTag); // second call should hit cache
		Assertions.assertEquals(1, results.size());

		verifyAll();	
	}

	private Tag createTag(String name, String value) {
		return Tag.builder().key(name).value(value).build();
	}

    @Test
    void shouldGetStackByEndAndIndex() throws WrongNumberOfStacksException {
        List<Tag> tagsA = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());
        List<Tag> tagsB = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());
        List<Tag> tagsC = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());

        tagsA.add(createTag(AwsFacade.INDEX_TAG,"4"));
        tagsB.add(createTag(AwsFacade.INDEX_TAG,"9"));

        List<Stack> list = new LinkedList<>();
        list.add(Stack.builder().stackName("matchingStack").tags(tagsA).stackId("anId").build());
        list.add(createStackWithTags("wrongStackB", tagsB));
        list.add(createStackWithTags("wrongStackC", tagsC));

        EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);

        replayAll();
        StackEntry result = repository.getStacknameByIndex(envTag, 4);
        verifyAll();

        Assertions.assertEquals("matchingStack", result.getStackName());
    }

	private Stack createStackWithTags(String stackName, List<Tag> tags) {
		return Stack.builder().stackName(stackName).tags(tags).build();
	}

	private Stack createStackWithTagsAndId(String stackName, String id, List<Tag> tags) {
		return Stack.builder().stackName(stackName).tags(tags).stackId(id).build();
	}

	@Test
    void shouldGetStackNameByUpdateIndexSingle() throws WrongNumberOfStacksException {
        List<Tag> tagsA = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());
        List<Tag> tagsB = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());
        List<Tag> tagsC = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());

        tagsA.add(createTag(AwsFacade.UPDATE_INDEX_TAG,"4"));
        tagsB.add(createTag(AwsFacade.INDEX_TAG,"3"));
        tagsC.add(createTag(AwsFacade.UPDATE_INDEX_TAG,"5"));

        List<Stack> list = new LinkedList<>();
        list.add(createStackWithTagsAndId("matchingStack","anId",tagsA));
        list.add(createStackWithTags("wrongStackB",tagsB));
        list.add(createStackWithTags("wrongStackC",tagsC));

        EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);

        replayAll();
        StackEntry result = repository.getStacknameByIndex(envTag, 4);
        verifyAll();

        Assertions.assertEquals("matchingStack", result.getStackName());
	}

    @Test
    void shouldGetStackNameByUpdateIndexMultiple() throws WrongNumberOfStacksException {
        List<Tag> tagsA = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());
        List<Tag> tagsB = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());
        List<Tag> tagsC = EnvironmentSetupForTests.createExpectedStackTags("comment",noBuildNumber, mainProjectAndEnv.getProject());

        tagsA.add(createTag(AwsFacade.UPDATE_INDEX_TAG,"3,4,8"));
        tagsB.add(createTag(AwsFacade.INDEX_TAG,"2"));
        tagsC.add(createTag(AwsFacade.UPDATE_INDEX_TAG,"5,6"));

        List<Stack> list = new LinkedList<>();
        list.add(createStackWithTagsAndId("matchingStack", "anId", tagsA));
        list.add(createStackWithTags("wrongStackB",tagsB));
        list.add(createStackWithTags("wrongStackC",tagsC));

        EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);

        replayAll();
        StackEntry result = repository.getStacknameByIndex(envTag, 4);
        verifyAll();

        Assertions.assertEquals("matchingStack", result.getStackName());
    }
	
	@Test
    void testShouldFindResourcesByIdInsideOfAStack()  {
		String logicalId = "logicalIdOfResource";
		String expectedPhyId = "physicalIdOfResource";
		String stackName = "testStackName";
		
		List<StackResource> stackResources = new LinkedList<>();
		stackResources.add(StackResource.builder().logicalResourceId("logWrong").physicalResourceId("phyWrong").build());
		stackResources.add(StackResource.builder().logicalResourceId(logicalId).physicalResourceId(expectedPhyId).build());
		
		List<Stack> list = new LinkedList<>();
		list.add(createStackWithTags(stackName,
				EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist")));
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(stackResources);
		replayAll();
		
		String physicalId = repository.findPhysicalIdByLogicalId(envTag, logicalId);		
		Assertions.assertEquals(expectedPhyId, physicalId);
		physicalId = repository.findPhysicalIdByLogicalId(envTag, logicalId);	 // hits cache	
		Assertions.assertEquals(expectedPhyId, physicalId);
		
		verifyAll();
	}

	private Stack createStackWithNameAndStatus(String name, StackStatus stackStatus, List<Tag> tags) {
		return Stack.builder().stackName(name).stackStatus(stackStatus).tags(tags).build();
	}
	
	@Test
    void testShouldWaitForStatusToChange() throws WrongNumberOfStacksException, InterruptedException {
		String stackName = "testStack";

		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_COMPLETE);
		
		replayAll();
		StackStatus result = repository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(StackMonitor.CREATE_ABORTS));
		Assertions.assertEquals(StackStatus.CREATE_COMPLETE, result);
		verifyAll();
	}
	
	@Test
    void testWaitForStatusShouldAbort() throws WrongNumberOfStacksException, InterruptedException {
		String stackName = "testStack";

		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_FAILED);
		
		replayAll();
		StackStatus result = repository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(StackMonitor.CREATE_ABORTS));
		Assertions.assertEquals(StackStatus.CREATE_FAILED, result);
		verifyAll();
	}
	
	@Test
    void testShouldCheckIfStackExists() {
		String stackName = "testStack";

		EasyMock.expect(formationClient.stackExists(stackName)).andReturn(true);
		replayAll();
		Assertions.assertTrue(repository.stackExists(stackName));
		verifyAll();
	}
	
	@Test
    void testShouldGetCurrentStackStatus() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		Stack inProgressStack = createStackWithNameAndStatus(stackName, StackStatus.CREATE_IN_PROGRESS,
				EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist"));
		
		Stack abortedStack = createStackWithNameAndStatus("someOtherName", StackStatus.CREATE_FAILED,
				EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist"));
		
		List<Stack> stacks = new LinkedList<>();
		stacks.add(inProgressStack);
		stacks.add(abortedStack);
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks); // cached after first call
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		
		replayAll();
		StackStatus result = repository.getStackStatus(stackName);
		Assertions.assertEquals(StackStatus.CREATE_IN_PROGRESS, result);
		result = repository.getStackStatus(stackName);
		Assertions.assertEquals(StackStatus.CREATE_IN_PROGRESS, result);
		verifyAll();
	}
	
	@Test
    void shouldThrowIfNoSuchStack() {
		List<Stack> stacks = new LinkedList<>();
		stacks.add(createStackWithNameAndStatus("ThisIsNotTheStackYouAreLookingFor", StackStatus.CREATE_COMPLETE,
				new LinkedList<>()));
			
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks); 

		replayAll();
        try {
            repository.getStackStatus("thisStackShouldNotExist");
            Assertions.fail("should not have reached here, exception expected");
        } catch (WrongNumberOfStacksException e) {
            // expected
        }
		verifyAll();
	}
	
	@Test
    void testShouldGetIdForAStackName() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		Stack stack = Stack.builder().tags(EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist")).
				stackName(stackName).
				stackId(stackId).build();
		
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = repository.getStackNameAndId(stackName);
		Assertions.assertEquals(stackName, result.getStackName());
		Assertions.assertEquals(stackId, result.getStackId());
		verifyAll();
	}
	
	@Test
    void shouldFindInstancesForProjectBasedOnCriteria() {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		String instanceId = "theInstanceId";
		SearchCriteria criteria = new SearchCriteria(EnvironmentSetupForTests.getMainProjectAndEnv());
		
		queryForInstancesExpectations(stackName, stackId, instanceId, "", noBuildNumber);
		
		replayAll();
		List<String> result = repository.getAllInstancesFor(criteria);
		Assertions.assertEquals(result.size(), 1);
		result = repository.getAllInstancesFor(criteria); // cached call
		Assertions.assertEquals(result.size(), 1);

		Assertions.assertEquals(instanceId, result.get(0));
		verifyAll();
	}

	private void queryForInstancesExpectations(String stackName, String stackId, String instanceId, String comment, Integer build) {
		List<StackResource> resources = new LinkedList<>();
		resources.add(StackResource.builder().resourceType("AWS::EC2::Instance").physicalResourceId(instanceId).build());
		resources.add(StackResource.builder().resourceType("AWS::EC2::ELB").physicalResourceId("notAnInstance").build());
		
		Stack stack = Stack.builder().tags(EnvironmentSetupForTests.createExpectedStackTags(comment, build, "CfnAssist")).
				stackName(stackName).
				stackId(stackId).
				stackStatus(StackStatus.CREATE_COMPLETE).build();
		
		List<Stack> list = new LinkedList<>();
		list.add(stack);
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(resources);
	}
	
	@Test
    void shouldFindInstancesForStack() {
		String stackName = "testStack";
		String instanceId = "theInstanceId";
		
		List<StackResource> resources = new LinkedList<>();
		resources.add(StackResource.builder().resourceType("AWS::EC2::Instance").physicalResourceId(instanceId).build());
		resources.add(StackResource.builder().resourceType("AWS::EC2::ELB").physicalResourceId("notAnInstance").build());
		
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(resources);
		
		replayAll();
		List<String> result = repository.getInstancesFor(stackName);
		Assertions.assertEquals(result.size(), 1);
		result = repository.getInstancesFor(stackName); // cached call
		verifyAll();
		
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(instanceId, result.get(0));

	}
	
	@Test
    void shouldFindInstancesForProjAndEnvMakingTypeTag() throws CfnAssistException {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		String instanceIdA = "InstanceIdA";
		String instanceIdB = "InstanceIdB";
		SearchCriteria criteria = new SearchCriteria(EnvironmentSetupForTests.getMainProjectAndEnv());
		
		List<StackResource> resources = new LinkedList<>();
		resources.add(StackResource.builder().resourceType("AWS::EC2::Instance").physicalResourceId(instanceIdA).build());
		resources.add(StackResource.builder().resourceType("AWS::EC2::Instance").physicalResourceId(instanceIdB).build());
		
		Stack stack = Stack.builder().tags(EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist")).
				stackName(stackName).
				stackId(stackId).
				stackStatus(StackStatus.CREATE_COMPLETE).build();
				
		List<Stack> stacks = new LinkedList<>();
		stacks.add(stack);
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks);
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(resources);
		String typeTag = "theTypeTag";
		EasyMock.expect(cloudRepository.getTagsForInstance(instanceIdA)).andStubReturn(withTags("0042", typeTag));
		EasyMock.expect(cloudRepository.getTagsForInstance(instanceIdB)).andStubReturn(withTags("0042", "wrongTypeTag"));
		replayAll();
		
		Set<Instance> result = repository.getAllInstancesMatchingType(criteria,typeTag);
		Assertions.assertEquals(result.size(), 1);
		result = repository.getAllInstancesMatchingType(criteria, typeTag); // cached call
		Assertions.assertEquals(result.size(), 1);

		List<Instance> resultAsList = new ArrayList<>(result);
		Assertions.assertEquals(instanceIdA, resultAsList.get(0).instanceId());
		verifyAll();
	}
	
	@Test
    void testShouldGetStackByName() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		
		List<Stack> list = new LinkedList<>();
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist");
		list.add(createStackWithTagsAndId(stackName, "correctId",tags));
		list.add(createStackWithTagsAndId("someOtherName", "wrongId", tags));
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		
		replayAll();
		Stack result = repository.getStack(stackName);
		Assertions.assertEquals(stackName, result.stackName());
		Assertions.assertEquals("correctId", result.stackId());
		result = repository.getStack(stackName); // cached on this call
		Assertions.assertEquals(stackName, result.stackName());
		Assertions.assertEquals("correctId", result.stackId());
		verifyAll();
	}
	
	@Test
    void shouldGetStacksMatchingProjectEnvAndName() {
		List<Stack> list = new LinkedList<>();

		list.add(createStackWithTagsAndId("CfnAssist1TestsimpleStack", "idA", createTags(1)));
		list.add(createStackWithTagsAndId("CfnAssist78TestsomeOtherName","idC",createTags(78)));
		list.add(createStackWithTagsAndId("CfnAssist2TestsimpleStack", "idB", createTags(2)));
		list.add(createStackWithTagsAndId("CfnAssistTestsomeOtherName","idE",
				EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist")));
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		
		replayAll();
		List<StackEntry> result = repository.getStacksMatching(mainProjectAndEnv.getEnvTag(),"simpleStack");
		verifyAll();
		
		Assertions.assertEquals(2, result.size());
		Assertions.assertEquals("idA", result.get(0).getStack().stackId());
		Assertions.assertEquals("idB", result.get(1).getStack().stackId());
	}
	
	private List<Tag> createTags(Integer buildNumber) {
		return EnvironmentSetupForTests.createExpectedStackTags("", buildNumber, "CfnAssist");
	}

	@Test
    void shouldDeleteStack() {
		// this smells, at least until we pull cache updates down into repository
		
		formationClient.deleteStack("stackName");
		EasyMock.expectLastCall();
		replayAll();
		
		repository.deleteStack("stackName");
		verifyAll();
	}
	
	@Test
    void shouldCreateStack() throws CfnAssistException {
		// this smells, at least until we pull cache updates down into repository
		
		Collection<Parameter> parameters = new LinkedList<>();
		MonitorStackEvents monitor = new PollingStackMonitor(repository);
		ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		Tagging tagging = new Tagging();
		EasyMock.expect(formationClient.createStack(projAndEnv, "contents", "stackName", 
				parameters, monitor, tagging)).andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.createStack(projAndEnv, "contents", "stackName", parameters, monitor, tagging);
		Assertions.assertEquals("stackName", result.getStackName());
		Assertions.assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
    void shouldUpdateStack() throws CfnAssistException {
		// this smells, at least until we pull cache updates down into repository
		
		Collection<Parameter> parameters = new LinkedList<>();
		MonitorStackEvents monitor = new PollingStackMonitor(repository);
        EasyMock.expect(formationClient.updateStack("contents", parameters, monitor, "stackName")).
                andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.updateStack("contents", parameters, monitor, "stackName");
		Assertions.assertEquals("stackName", result.getStackName());
		Assertions.assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
    void shouldUpdateStackWithSNS() throws CfnAssistException {
		// this smells, at least until we pull cache updates down into repository
		SNSMonitor snsMonitor = createMock(SNSMonitor.class);
		
		Collection<Parameter> parameters = new LinkedList<>();
		EasyMock.expect(formationClient.updateStack("contents", parameters, snsMonitor, "stackName"))
                .andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.updateStack("contents", parameters, snsMonitor, "stackName");
		Assertions.assertEquals("stackName", result.getStackName());
		Assertions.assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
    void shouldValidateTemplates() {
		
		List<TemplateParameter> params = new LinkedList<>();
		params.add(TemplateParameter.builder().defaultValue("aDefaultValue").build());
		EasyMock.expect(formationClient.validateTemplate("someContents")).andReturn(params);
		
		replayAll();
		List<TemplateParameter> result = repository.validateStackTemplate("someContents");
		Assertions.assertEquals(1, result.size());
	}

	private List<software.amazon.awssdk.services.ec2.model.Tag> withTags(String buildNumber, String typeTag) {
		List<software.amazon.awssdk.services.ec2.model.Tag> tags = new LinkedList<>();
		tags.add(EnvironmentSetupForTests.createEc2Tag(AwsFacade.BUILD_TAG,buildNumber));
		tags.add(EnvironmentSetupForTests.createEc2Tag(AwsFacade.TYPE_TAG, typeTag));
		return tags;
	}
		
}

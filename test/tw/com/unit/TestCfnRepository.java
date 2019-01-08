package tw.com.unit;

import com.amazonaws.services.elasticloadbalancing.model.Instance;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.cloudformation.model.*;
import tw.com.*;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CFNClient;
import tw.com.repository.CfnRepository;
import tw.com.repository.CloudRepository;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestCfnRepository extends EasyMockSupport {
	
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);

    private CFNClient formationClient;
	private CfnRepository repository;
	private EnvironmentTag envTag;
	private Integer noBuildNumber = -1;

	private CloudRepository cloudRepository;
	
	@Before
	public void beforeEachTestRuns() {
		formationClient = createMock(CFNClient.class);
		
		cloudRepository = createMock(CloudRepository.class);
		repository = new CfnRepository(formationClient, cloudRepository, mainProjectAndEnv.getProject());	
		envTag = new EnvironmentTag(EnvironmentSetupForTests.ENV);
	}

	@Test
	public void testGetAllStacks() {
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist");
		List<Stack> list = new LinkedList<>();
		list.add(Stack.builder().tags(tags).stackName("matchingStack").build());
		list.add(Stack.builder().stackName("noMatchingTags").build());
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		replayAll();
			
		List<StackEntry> results = repository.getStacks();
		assertEquals(1, results.size());
		results = repository.getStacks(); // second call should hit cache
		assertEquals(1, results.size());

		verifyAll();	
	}
	
	@Test
	public void testGetStacksByEnv() {
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
		assertEquals(1, results.size());
		results = repository.getStacks(envTag); // second call should hit cache
		assertEquals(1, results.size());

		verifyAll();	
	}

	private Tag createTag(String name, String value) {
		return Tag.builder().key(name).value(value).build();
	}

    @Test
    public void shouldGetStackByEndAndIndex() throws WrongNumberOfStacksException {
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

        assertEquals("matchingStack", result.getStackName());
    }

	private Stack createStackWithTags(String stackName, List<Tag> tags) {
		return Stack.builder().stackName(stackName).tags(tags).build();
	}

	private Stack createStackWithTagsAndId(String stackName, String id, List<Tag> tags) {
		return Stack.builder().stackName(stackName).tags(tags).stackId(id).build();
	}

	@Test
	public void shouldGetStackNameByUpdateIndexSingle() throws WrongNumberOfStacksException {
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

        assertEquals("matchingStack", result.getStackName());
	}

    @Test
    public void shouldGetStackNameByUpdateIndexMultiple() throws WrongNumberOfStacksException {
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

        assertEquals("matchingStack", result.getStackName());
    }
	
	@Test 
	public void testShouldFindResourcesByIdInsideOfAStack()  {
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
		assertEquals(expectedPhyId, physicalId);
		physicalId = repository.findPhysicalIdByLogicalId(envTag, logicalId);	 // hits cache	
		assertEquals(expectedPhyId, physicalId);
		
		verifyAll();
	}

	private Stack createStackWithNameAndStatus(String name, StackStatus stackStatus, List<Tag> tags) {
		return Stack.builder().stackName(name).stackStatus(stackStatus).tags(tags).build();
	}
	
	@Test
	public void testShouldWaitForStatusToChange() throws WrongNumberOfStacksException, InterruptedException {
		String stackName = "testStack";

		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_COMPLETE);
		
		replayAll();
		StackStatus result = repository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(StackMonitor.CREATE_ABORTS));
		assertEquals(StackStatus.CREATE_COMPLETE, result);
		verifyAll();
	}
	
	@Test
	public void testWaitForStatusShouldAbort() throws WrongNumberOfStacksException, InterruptedException {
		String stackName = "testStack";

		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_IN_PROGRESS);
		EasyMock.expect(formationClient.currentStatus(stackName)).andReturn(StackStatus.CREATE_FAILED);
		
		replayAll();
		StackStatus result = repository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(StackMonitor.CREATE_ABORTS));
		assertEquals(StackStatus.CREATE_FAILED, result);
		verifyAll();
	}
	
	@Test
	public void testShouldCheckIfStackExists() {
		String stackName = "testStack";

		EasyMock.expect(formationClient.stackExists(stackName)).andReturn(true);
		replayAll();
		assertTrue(repository.stackExists(stackName));
		verifyAll();
	}
	
	@Test
	public void testShouldGetCurrentStackStatus() throws WrongNumberOfStacksException {
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
		assertEquals(StackStatus.CREATE_IN_PROGRESS, result);
		result = repository.getStackStatus(stackName);
		assertEquals(StackStatus.CREATE_IN_PROGRESS, result);
		verifyAll();
	}
	
	@Test
	public void shouldThrowIfNoSuchStack() {
		List<Stack> stacks = new LinkedList<>();
		stacks.add(createStackWithNameAndStatus("ThisIsNotTheStackYouAreLookingFor", StackStatus.CREATE_COMPLETE,
				new LinkedList<>()));
			
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks); 

		replayAll();
        try {
            repository.getStackStatus("thisStackShouldNotExist");
            fail("should not have reached here, exception expected");
        } catch (WrongNumberOfStacksException e) {
            // expected
        }
		verifyAll();
	}
	
	@Test
	public void testShouldGetIdForAStackName() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		Stack stack = Stack.builder().tags(EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist")).
				stackName(stackName).
				stackId(stackId).build();
		
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = repository.getStackNameAndId(stackName);
		assertEquals(stackName, result.getStackName());
		assertEquals(stackId, result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldFindInstancesForProjectBasedOnCriteria() {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		String instanceId = "theInstanceId";
		SearchCriteria criteria = new SearchCriteria(EnvironmentSetupForTests.getMainProjectAndEnv());
		
		queryForInstancesExpectations(stackName, stackId, instanceId, "", noBuildNumber);
		
		replayAll();
		List<String> result = repository.getAllInstancesFor(criteria);
		assertEquals(result.size(),1);
		result = repository.getAllInstancesFor(criteria); // cached call
		assertEquals(result.size(),1);

		assertEquals(instanceId, result.get(0));
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
	public void shouldFindInstancesForStack() {
		String stackName = "testStack";
		String instanceId = "theInstanceId";
		
		List<StackResource> resources = new LinkedList<>();
		resources.add(StackResource.builder().resourceType("AWS::EC2::Instance").physicalResourceId(instanceId).build());
		resources.add(StackResource.builder().resourceType("AWS::EC2::ELB").physicalResourceId("notAnInstance").build());
		
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(resources);
		
		replayAll();
		List<String> result = repository.getInstancesFor(stackName);
		assertEquals(result.size(),1);
		result = repository.getInstancesFor(stackName); // cached call
		verifyAll();
		
		assertEquals(result.size(),1);
		assertEquals(instanceId, result.get(0));

	}
	
	@Test
	public void shouldFindInstancesForProjAndEnvMakingTypeTag() throws CfnAssistException {
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
		
		List<Instance> result = repository.getAllInstancesMatchingType(criteria,typeTag);
		assertEquals(result.size(),1);
		result = repository.getAllInstancesMatchingType(criteria, typeTag); // cached call
		assertEquals(result.size(),1);

		assertEquals(instanceIdA, result.get(0).getInstanceId());
		verifyAll();
	}
	
	@Test
	public void testShouldGetStackByName() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		
		List<Stack> list = new LinkedList<>();
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("",noBuildNumber, "CfnAssist");
		list.add(createStackWithTagsAndId(stackName, "correctId",tags));
		list.add(createStackWithTagsAndId("someOtherName", "wrongId", tags));
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		
		replayAll();
		Stack result = repository.getStack(stackName);
		assertEquals(stackName, result.stackName());
		assertEquals("correctId", result.stackId());
		result = repository.getStack(stackName); // cached on this call
		assertEquals(stackName, result.stackName());
		assertEquals("correctId", result.stackId());
		verifyAll();
	}
	
	@Test
	public void shouldGetStacksMatchingProjectEnvAndName() {
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
		
		assertEquals(2, result.size());
		assertEquals("idA", result.get(0).getStack().stackId());
		assertEquals("idB", result.get(1).getStack().stackId());
	}
	
	private List<Tag> createTags(Integer buildNumber) {
		return EnvironmentSetupForTests.createExpectedStackTags("", buildNumber, "CfnAssist");
	}

	@Test
	public void shouldDeleteStack() {
		// this smells, at least until we pull cache updates down into repository
		
		formationClient.deleteStack("stackName");
		EasyMock.expectLastCall();
		replayAll();
		
		repository.deleteStack("stackName");
		verifyAll();
	}
	
	@Test
	public void shouldCreateStack() throws CfnAssistException {
		// this smells, at least until we pull cache updates down into repository
		
		Collection<Parameter> parameters = new LinkedList<>();
		MonitorStackEvents monitor = new PollingStackMonitor(repository);
		ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		Tagging tagging = new Tagging();
		EasyMock.expect(formationClient.createStack(projAndEnv, "contents", "stackName", 
				parameters, monitor, tagging)).andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.createStack(projAndEnv, "contents", "stackName", parameters, monitor, tagging);
		assertEquals("stackName", result.getStackName());
		assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldUpdateStack() throws CfnAssistException {
		// this smells, at least until we pull cache updates down into repository
		
		Collection<Parameter> parameters = new LinkedList<>();
		MonitorStackEvents monitor = new PollingStackMonitor(repository);
        EasyMock.expect(formationClient.updateStack("contents", parameters, monitor, "stackName")).
                andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.updateStack("contents", parameters, monitor, "stackName");
		assertEquals("stackName", result.getStackName());
		assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldUpdateStackWithSNS() throws CfnAssistException {
		// this smells, at least until we pull cache updates down into repository
		SNSMonitor snsMonitor = createMock(SNSMonitor.class);
		
		Collection<Parameter> parameters = new LinkedList<>();
		EasyMock.expect(formationClient.updateStack("contents", parameters, snsMonitor, "stackName"))
                .andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.updateStack("contents", parameters, snsMonitor, "stackName");
		assertEquals("stackName", result.getStackName());
		assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldValidateTemplates() {
		
		List<TemplateParameter> params = new LinkedList<>();
		params.add(TemplateParameter.builder().defaultValue("aDefaultValue").build());
		EasyMock.expect(formationClient.validateTemplate("someContents")).andReturn(params);
		
		replayAll();
		List<TemplateParameter> result = repository.validateStackTemplate("someContents");
		assertEquals(1, result.size());
	}

	private List<software.amazon.awssdk.services.ec2.model.Tag> withTags(String buildNumber, String typeTag) {
		List<software.amazon.awssdk.services.ec2.model.Tag> tags = new LinkedList<>();
		tags.add(EnvironmentSetupForTests.createEc2Tag(AwsFacade.BUILD_TAG,buildNumber));
		tags.add(EnvironmentSetupForTests.createEc2Tag(AwsFacade.TYPE_TAG, typeTag));
		return tags;
	}
		
}

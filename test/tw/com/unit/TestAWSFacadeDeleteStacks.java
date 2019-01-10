package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import software.amazon.awssdk.services.iam.model.User;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.*;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@RunWith(EasyMockRunner.class)
public class TestAWSFacadeDeleteStacks extends EasyMockSupport {
	private static final StackStatus DELETE_COMP_STATUS = StackStatus.DELETE_COMPLETE;
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private ELBRepository elbRepository;
	private MonitorStackEvents monitor;
	private String project = projectAndEnv.getProject();
	private EnvironmentTag environmentTag = projectAndEnv.getEnvTag();
	private NotificationSender notificationSender;
	private IdentityProvider identityProvider;
	private User user;

	@Before
	public void beforeEachTestRuns() {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		VpcRepository vpcRepository = createMock(VpcRepository.class);
		elbRepository = createMock(ELBRepository.class);
		CloudRepository cloudRepository = createStrictMock(CloudRepository.class);
		notificationSender = createStrictMock(NotificationSender.class);
		identityProvider = createStrictMock(IdentityProvider.class);
		
		user = EnvironmentSetupForTests.createUser();

		LogRepository logRepository = createStrictMock(LogRepository.class);
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, logRepository);
	}
	
	@Test
	public void shouldDeleteStack() throws InterruptedException, CfnAssistException {
		String fullName = "CfnAssistTestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(fullName, stackId);
		
		setDeleteExpectations(fullName, stackNameAndId);
		
		replayAll();
		aws.deleteStackFrom(file, projectAndEnv);
		verifyAll();
	}

	@Test
	public void shouldDeleteByName() throws CfnAssistException, InterruptedException {
        String fullName = "CfnAssistTestsimpleStack";
        String stackId = "stackId";
        StackNameAndId stackNameAndId = new StackNameAndId(fullName, stackId);

        setDeleteExpectations(fullName, stackNameAndId);

        replayAll();
        aws.deleteStackByName("simpleStack", projectAndEnv);
        verifyAll();
	}
	
	@Test
	public void shouldDeleteStackWithBuildNumber() throws InterruptedException, CfnAssistException {
		String fullName = "CfnAssist57TestsimpleStack";
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		String stackId = "stackId";
		StackNameAndId stackNameAndId = new StackNameAndId(fullName, stackId);
		
		projectAndEnv.addBuildNumber(57);
		setDeleteExpectations(fullName, stackNameAndId);
		
		replayAll();
		aws.deleteStackFrom(file, projectAndEnv);
		verifyAll();
	}

    @Test
    public void shouldDeleteByNameWithBuildNumber() throws CfnAssistException, InterruptedException {
        String fullName = "CfnAssist57TestsimpleStack";
        String stackId = "stackId";
        StackNameAndId stackNameAndId = new StackNameAndId(fullName, stackId);

        projectAndEnv.addBuildNumber(57);
        setDeleteExpectations(fullName, stackNameAndId);

        replayAll();
        aws.deleteStackByName("simpleStack", projectAndEnv);
        verifyAll();
    }
	
	@Test
	public void shouldDeleteNamedStacksNotAssociatedWithLB() throws InterruptedException, CfnAssistException {
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		
		Stack stackA = createStack("CfnAssist0057TestsimpleStack", "idA");
		Stack stackB = createStack("CfnAssist0058TestsimpleStack", "idB");
		Stack stackC = createStack("CfnAssist0059TestsimpleStack", "idC"); // only this one associated with LB
		
		List<StackEntry> stacksForProj = new LinkedList<>();
		stacksForProj.add(new StackEntry(project, environmentTag, stackA));
		stacksForProj.add(new StackEntry(project, environmentTag, stackB));
		stacksForProj.add(new StackEntry(project, environmentTag, stackC));
		List<Instance> elbInstances = new LinkedList<>();
		
		elbInstances.add(Instance.builder().instanceId("matchingInstanceId").build());
		
		EasyMock.expect(elbRepository.findInstancesAssociatedWithLB(projectAndEnv,"typeTag")).andReturn(elbInstances);
		EasyMock.expect(cfnRepository.getStacksMatching(environmentTag,"simpleStack")).andReturn(stacksForProj);	
		EasyMock.expect(cfnRepository.getInstancesFor(stackA.stackName())).andReturn(createInstancesFor("123"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackB.stackName())).andReturn(createInstancesFor("567"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackC.stackName())).andReturn(createInstancesFor("matchingInstanceId"));
		
		setDeleteExpectations(stackA.stackName(), createNameAndId(stackA));
		setDeleteExpectations(stackB.stackName(), createNameAndId(stackB));
		
		replayAll();
		aws.tidyNonLBAssocStacks(file, projectAndEnv,"typeTag");
		verifyAll();
	}

	private Stack createStack(String name, String id) {
		return Stack.builder().stackName(name).stackId(id).build();
	}

	@Test
	public void shouldDeleteNamedStacksNotAssociatedWithLBWhileIgnoringStacksWithNoInstances() throws InterruptedException, CfnAssistException {
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		
		Stack stackA = createStack("CfnAssist0057TestsimpleStack","idA"); // this one has no instances
		Stack stackB = createStack("CfnAssist0058TestsimpleStack","idB");
		Stack stackC = createStack("CfnAssist0059TestsimpleStack","idC"); // only this one associated with LB
		
		List<StackEntry> stacksForProj = new LinkedList<>();
		stacksForProj.add(new StackEntry(project, environmentTag, stackA));
		stacksForProj.add(new StackEntry(project, environmentTag, stackB));
		stacksForProj.add(new StackEntry(project, environmentTag, stackC));
		List<Instance> elbInstances = new LinkedList<>();
		
		elbInstances.add(Instance.builder().instanceId("matchingInstanceId").build());
		
		EasyMock.expect(elbRepository.findInstancesAssociatedWithLB(projectAndEnv,"typeTag")).andReturn(elbInstances);
		EasyMock.expect(cfnRepository.getStacksMatching(environmentTag,"simpleStack")).andReturn(stacksForProj);	
		EasyMock.expect(cfnRepository.getInstancesFor(stackA.stackName())).andReturn(new LinkedList<>());
		EasyMock.expect(cfnRepository.getInstancesFor(stackB.stackName())).andReturn(createInstancesFor("567"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackC.stackName())).andReturn(createInstancesFor("matchingInstanceId"));
		
		setDeleteExpectations(stackB.stackName(), createNameAndId(stackB));
		
		replayAll();
		aws.tidyNonLBAssocStacks(file, projectAndEnv,"typeTag");
		verifyAll();
	}
	
	private void setDeleteExpectations(String stackName,
			StackNameAndId stackNameAndId) throws InterruptedException, CfnAssistException {
		EasyMock.expect(cfnRepository.getStackNameAndId(stackName)).andReturn(stackNameAndId);
		cfnRepository.deleteStack(stackName);
		EasyMock.expectLastCall();
		EasyMock.expect(monitor.waitForDeleteFinished(stackNameAndId)).andReturn(DELETE_COMP_STATUS);
		EasyMock.expect(identityProvider.getUserId()).andReturn(user);
		CFNAssistNotification notification = new CFNAssistNotification(stackName, DELETE_COMP_STATUS.toString(), user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("ifOfSentMessage");
	}
	
	private List<String> createInstancesFor(String id) {
		List<String> instances = new LinkedList<>();
		instances.add(id);
		return instances;
	}

	private StackNameAndId createNameAndId(Stack stack) {
		return new StackNameAndId(stack.stackName(), stack.stackId());
	}
}

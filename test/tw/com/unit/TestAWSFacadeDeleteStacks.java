package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.identitymanagement.model.User;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@RunWith(EasyMockRunner.class)
public class TestAWSFacadeDeleteStacks extends EasyMockSupport {
	private static final String DELETE_COMP_STATUS = StackStatus.DELETE_COMPLETE.toString();
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
		
		user = new User("path", "userName", "userId", "arn", new Date());

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider);
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
		
		Stack stackA = new Stack().withStackName("CfnAssist0057TestsimpleStack").withStackId("idA");
		Stack stackB = new Stack().withStackName("CfnAssist0058TestsimpleStack").withStackId("idB");
		Stack stackC = new Stack().withStackName("CfnAssist0059TestsimpleStack").withStackId("idC"); // only this one associated with LB
		
		List<StackEntry> stacksForProj = new LinkedList<>();
		stacksForProj.add(new StackEntry(project, environmentTag, stackA));
		stacksForProj.add(new StackEntry(project, environmentTag, stackB));
		stacksForProj.add(new StackEntry(project, environmentTag, stackC));
		List<Instance> elbInstances = new LinkedList<>();
		
		elbInstances.add(new Instance().withInstanceId("matchingInstanceId"));
		
		EasyMock.expect(elbRepository.findInstancesAssociatedWithLB(projectAndEnv,"typeTag")).andReturn(elbInstances);
		EasyMock.expect(cfnRepository.getStacksMatching(environmentTag,"simpleStack")).andReturn(stacksForProj);	
		EasyMock.expect(cfnRepository.getInstancesFor(stackA.getStackName())).andReturn(createInstancesFor("123"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackB.getStackName())).andReturn(createInstancesFor("567"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackC.getStackName())).andReturn(createInstancesFor("matchingInstanceId"));
		
		setDeleteExpectations(stackA.getStackName(), createNameAndId(stackA));
		setDeleteExpectations(stackB.getStackName(), createNameAndId(stackB));
		
		replayAll();
		aws.tidyNonLBAssocStacks(file, projectAndEnv,"typeTag");
		verifyAll();
	}
	
	@Test
	public void shouldDeleteNamedStacksNotAssociatedWithLBWhileIgnoringStacksWithNoInstances() throws InterruptedException, CfnAssistException {
		String filename = FilesForTesting.SIMPLE_STACK;
		File file = new File(filename);	
		
		Stack stackA = new Stack().withStackName("CfnAssist0057TestsimpleStack").withStackId("idA"); // this one has no instances
		Stack stackB = new Stack().withStackName("CfnAssist0058TestsimpleStack").withStackId("idB");
		Stack stackC = new Stack().withStackName("CfnAssist0059TestsimpleStack").withStackId("idC"); // only this one associated with LB
		
		List<StackEntry> stacksForProj = new LinkedList<>();
		stacksForProj.add(new StackEntry(project, environmentTag, stackA));
		stacksForProj.add(new StackEntry(project, environmentTag, stackB));
		stacksForProj.add(new StackEntry(project, environmentTag, stackC));
		List<Instance> elbInstances = new LinkedList<>();
		
		elbInstances.add(new Instance().withInstanceId("matchingInstanceId"));
		
		EasyMock.expect(elbRepository.findInstancesAssociatedWithLB(projectAndEnv,"typeTag")).andReturn(elbInstances);
		EasyMock.expect(cfnRepository.getStacksMatching(environmentTag,"simpleStack")).andReturn(stacksForProj);	
		EasyMock.expect(cfnRepository.getInstancesFor(stackA.getStackName())).andReturn(new LinkedList<>());
		EasyMock.expect(cfnRepository.getInstancesFor(stackB.getStackName())).andReturn(createInstancesFor("567"));
		EasyMock.expect(cfnRepository.getInstancesFor(stackC.getStackName())).andReturn(createInstancesFor("matchingInstanceId"));
		
		setDeleteExpectations(stackB.getStackName(), createNameAndId(stackB));
		
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
		CFNAssistNotification notification = new CFNAssistNotification(stackName, DELETE_COMP_STATUS, user);
		EasyMock.expect(notificationSender.sendNotification(notification)).andReturn("ifOfSentMessage");
	}
	
	private List<String> createInstancesFor(String id) {
		List<String> instances = new LinkedList<>();
		instances.add(id);
		return instances;
	}

	private StackNameAndId createNameAndId(Stack stack) {
		return new StackNameAndId(stack.getStackName(), stack.getStackId());
	}
}

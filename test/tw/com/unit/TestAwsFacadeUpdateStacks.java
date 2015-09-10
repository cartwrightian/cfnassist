package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.Vpc;
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
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.*;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;


@RunWith(EasyMockRunner.class)
public class TestAwsFacadeUpdateStacks extends EasyMockSupport {
	private static final String VPC_ID = "vpcId";
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private MonitorStackEvents monitor;
	private ELBRepository elbRepository;
	private CloudRepository cloudRepository;
	private NotificationSender notificationSender;
	private IdentityProvider identityProvider;
	private String regionName = EnvironmentSetupForTests.getRegion().getName();
	private Map<String, AvailabilityZone> zones = new HashMap<>();

	@Before
	public void beforeEachTestRuns() {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		elbRepository = createMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
		notificationSender = createStrictMock(NotificationSender.class);
		identityProvider = createStrictMock(IdentityProvider.class);

		String regionName = EnvironmentSetupForTests.getRegion().getName();
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, regionName);
	}
	
	@Test
	public void voidShouldUpdateAnExistingStackNoParams() throws IOException, CfnAssistException, InterruptedException {
		String filename = FilesForTesting.SUBNET_STACK_DELTA;
		String stackName = "CfnAssistTestsubnet";
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(new TemplateParameter().withParameterKey("stackname").withDefaultValue("subnet"));
		Collection<Parameter> parameters = new LinkedList<>();

		StackNameAndId stackNameAndId = setUpdateExpectations(stackName, filename, templateParameters, parameters);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(stackNameAndId, result);
		verifyAll();	
	}
	
	@Test
	public void voidShouldUpdateAnExistingStackWithBuiltInParams() throws IOException, CfnAssistException, InterruptedException {
		String filename = FilesForTesting.SUBNET_STACK_DELTA;
		String stackName = "CfnAssistTestsubnet";
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(new TemplateParameter().withParameterKey("stackname").withDefaultValue("subnet"));
		templateParameters.add(new TemplateParameter().withParameterKey("env").withDefaultValue(projectAndEnv.getEnv()));
		templateParameters.add(new TemplateParameter().withParameterKey("vpc").withDefaultValue(VPC_ID));

		Collection<Parameter> creationParameters = new LinkedList<>();
		TestAwsFacadeCreatesStacks.addParam(creationParameters, "env", projectAndEnv.getEnv());
		TestAwsFacadeCreatesStacks.addParam(creationParameters, "vpc", VPC_ID);

		StackNameAndId stackNameAndId = setUpdateExpectations(stackName, filename, templateParameters, creationParameters);
		
		replayAll();
		StackNameAndId result = aws.applyTemplate(filename, projectAndEnv);
		assertEquals(stackNameAndId, result);
		verifyAll();	
	}
	
	@Test
	public void voidShouldUpdateAnExistingStackUserParams() throws IOException, CfnAssistException, InterruptedException {
		String filename = FilesForTesting.SUBNET_STACK_DELTA;
		String stackName = "CfnAssistTestsubnet";
		
		List<TemplateParameter> templateParameters = new LinkedList<>();
		templateParameters.add(new TemplateParameter().withParameterKey("stackname").withDefaultValue("subnet"));
		Collection<Parameter> userParameters  = new LinkedList<>();
		Collection<Parameter> creationParameters = new LinkedList<>();
		TestAwsFacadeCreatesStacks.addParam(userParameters, "userKey", "value");
		TestAwsFacadeCreatesStacks.addParam(creationParameters, "userKey", "value");

		StackNameAndId stackNameAndId = setUpdateExpectations(stackName, filename, templateParameters, creationParameters);
		
		replayAll();

		StackNameAndId result = aws.applyTemplate(new File(filename), projectAndEnv, userParameters);
		assertEquals(stackNameAndId, result);
		verifyAll();	
	}

	private StackNameAndId setUpdateExpectations(String stackName, String filename, List<TemplateParameter> templateParameters, 
			Collection<Parameter> parameters) throws InvalidStackParameterException, WrongNumberOfStacksException, InterruptedException, WrongStackStatus, NotReadyException, IOException {
		String stackId = "stackId";
		Stack stack = new Stack().withStackId(stackId);
		StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);
		
		String contents = EnvironmentSetupForTests.loadFile(filename);
		EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(new Vpc().withVpcId(VPC_ID));
		EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);
		EasyMock.expect(cfnRepository.updateStack(contents, parameters, monitor, stackName)).andReturn(stackNameAndId);
		EasyMock.expect(monitor.waitForUpdateFinished(stackNameAndId)).andReturn(StackStatus.UPDATE_COMPLETE.toString());
		EasyMock.expect(cfnRepository.updateSuccess(stackNameAndId)).andReturn(stack);
		EasyMock.expect(cloudRepository.getZones(regionName)).andReturn(zones);
		return stackNameAndId;
	}
	
	// TODO test that we throw on no stackname to update

}

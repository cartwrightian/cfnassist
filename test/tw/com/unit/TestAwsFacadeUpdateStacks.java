package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import org.easymock.EasyMockRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.*;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;


@RunWith(EasyMockRunner.class)
public class TestAwsFacadeUpdateStacks extends UpdateStackExpectations {
	private AwsFacade aws;

	@Before
	public void beforeEachTestRuns() {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createMock(CloudFormRepository.class);
		vpcRepository = createMock(VpcRepository.class);
		ELBRepository elbRepository = createMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
		NotificationSender notificationSender = createStrictMock(NotificationSender.class);
		IdentityProvider identityProvider = createStrictMock(IdentityProvider.class);

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider);
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

	// TODO test that we throw on no stackname to update

}

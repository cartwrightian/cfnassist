package tw.com;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class TestStackBuilder {
	private AwsProvider awsProvider;
	private static final String ENV = "Test";

	@Before
	public void beforeTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		awsProvider = new AwsFacade(credentialsProvider);
	}

	@Test
	public void canBuildAndDeleteSimpleStack() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, InvalidParameterException {	
		File templateFile = new File("src/cfnScripts/subnet.json");
		StackBuilder builder = new StackBuilder(awsProvider, ENV, templateFile);
		String stackName = builder.createStack();
		
		validateCheckAndDeleteWorks(stackName);
	}
	
	@Test
	public void canPassInSimpleParameter() throws FileNotFoundException, IOException, InvalidParameterException, 
			WrongNumberOfStacksException, InterruptedException {
		File templateFile = new File("src/cfnScripts/subnetWithParam.json");
		StackBuilder builder = new StackBuilder(awsProvider, ENV, templateFile);
		String stackName = builder.addParameter("zoneA", "eu-west-1a").createStack();
		
		validateCheckAndDeleteWorks(stackName);
	}

	private void validateCheckAndDeleteWorks(String stackName)
			throws WrongNumberOfStacksException, InterruptedException {
		String status = awsProvider.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		awsProvider.deleteStack(stackName);
		
		status = awsProvider.waitForDeleteFinished(stackName);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
	}

}

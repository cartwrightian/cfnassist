package tw.com;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class TestExecuteScriptsInOrderFromDir {
	
	public static final String FOLDER_PATH = "src/cfnScripts/orderedScripts";
	private static String env = TestAwsFacade.ENV;
	private static String proj = TestAwsFacade.PROJECT;
	ArrayList<String> expectedList = new ArrayList<String>();
	private AwsFacade aws;
	private int expectedSize;
	
	@Before 
	public void beforeAllTestsRun() {
		createExpectedNames();	
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, TestAwsFacade.getRegion());
	}
	
	@After
	public void afterAllTestsHaveRun() {	
		for(int i=expectedSize-1; i>=0; i--) {
			String stackName = expectedList.get(i);
			aws.deleteStack(stackName);
			try {
				aws.waitForDeleteFinished(stackName);
			} catch (WrongNumberOfStacksException | InterruptedException e) {
				// nothing we can do now, but do want to try and delete the other stacks
			}
		}
	}

	@Test
	public void shouldCreateTheStacks() throws WrongNumberOfStacksException, InterruptedException, FileNotFoundException, InvalidParameterException, IOException {
		ArrayList<String> stackNames = aws.applyTemplatesFromFolder(FOLDER_PATH, TestAwsFacade.PROJECT, env);
		
		assertEquals(expectedSize, stackNames.size());
		
		for(int i=0; i<expectedSize; i++) {
			String createdStackName = stackNames.get(i);
			assertEquals(expectedList.get(i), createdStackName);
			String status = aws.waitForCreateFinished(createdStackName);
			assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		}
	}

	private void createExpectedNames() {
		expectedList.add(proj+env+"01createSubnet");
		expectedList.add(proj+env+"02createAcls");
		expectedSize = expectedList.size();
	}

}

package tw.com;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class TestExecuteScriptsInOrderFromDir {
	
	private static final String THIRD_FILE = "03createRoutes.json";
	public static final String FOLDER_PATH = "src/cfnScripts/orderedScripts";
	Path srcFile = FileSystems.getDefault().getPath(FOLDER_PATH, "holding", THIRD_FILE);
	Path destFile = FileSystems.getDefault().getPath(FOLDER_PATH, THIRD_FILE);
	
	private static String env = TestAwsFacade.ENV;
	private static String proj = TestAwsFacade.PROJECT;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj,env);

	ArrayList<String> expectedList = new ArrayList<String>();
	private AwsFacade aws;
	//private int expectedSize;
	
	@Before 
	public void beforeAllTestsRun() throws IOException {
		createExpectedNames();	
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, TestAwsFacade.getRegion());
		Files.deleteIfExists(destFile);
		aws.resetDeltaIndex(mainProjectAndEnv);
	}
	
	@After
	public void afterAllTestsHaveRun() throws IOException {	
		for(int i=expectedList.size()-1; i>=0; i--) {
			String stackName = expectedList.get(i);
			aws.deleteStack(stackName);
			try {
				aws.waitForDeleteFinished(stackName);
			} catch (WrongNumberOfStacksException | InterruptedException e) {
				// nothing we can do now, but do want to try and delete the other stacks
			}
		}
		aws.resetDeltaIndex(mainProjectAndEnv);
		Files.deleteIfExists(destFile);
	}

	@Test
	public void shouldCreateTheStacksRequiredOnly() throws WrongNumberOfStacksException, InterruptedException, FileNotFoundException, InvalidParameterException, IOException {
		ArrayList<String> stackNames = aws.applyTemplatesFromFolder(FOLDER_PATH, mainProjectAndEnv);
		
		assertEquals(expectedList.size(), stackNames.size());
		
		for(int i=0; i<expectedList.size(); i++) {
			String createdStackName = stackNames.get(i);
			assertEquals(expectedList.get(i), createdStackName);
			String status = aws.waitForCreateFinished(createdStackName);
			assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		}
		
		// we are up to date, should not apply the files again
		stackNames = aws.applyTemplatesFromFolder(FOLDER_PATH, mainProjectAndEnv);
		assertEquals(0, stackNames.size());
		
		// copy in extra files to dir
		FileUtils.copyFile(srcFile.toFile(), destFile.toFile());
		stackNames = aws.applyTemplatesFromFolder(FOLDER_PATH, mainProjectAndEnv);
		assertEquals(1, stackNames.size());
		
		expectedList.add(proj+env+"03createRoutes");
		assertEquals(expectedList.get(2), stackNames.get(0));
	}

	private void createExpectedNames() {
		expectedList.add(proj+env+"01createSubnet");
		expectedList.add(proj+env+"02createAcls");
	}

}
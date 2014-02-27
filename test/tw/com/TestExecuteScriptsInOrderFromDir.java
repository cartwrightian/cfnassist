package tw.com;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.ec2.AmazonEC2Client;

public class TestExecuteScriptsInOrderFromDir {
	
	private static final String THIRD_FILE = "03createRoutes.json";
	Path srcFile = FileSystems.getDefault().getPath(EnvironmentSetupForTests.FOLDER_PATH, "holding", THIRD_FILE);
	Path destFile = FileSystems.getDefault().getPath(EnvironmentSetupForTests.FOLDER_PATH, THIRD_FILE);
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client ec2Client;
	
	private static String env = EnvironmentSetupForTests.ENV;
	private static String proj = EnvironmentSetupForTests.PROJECT;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj,env);

	ArrayList<String> expectedList = new ArrayList<String>();
	private AwsFacade aws;
	private MonitorStackEvents monitor;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}
	
	@Before 
	public void beforeAllTestsRun() throws IOException, CannotFindVpcException {
		createExpectedNames();	
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new PollingStackMonitor(cfnRepository);	
		aws = new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository);
		Files.deleteIfExists(destFile);
		aws.resetDeltaIndex(mainProjectAndEnv);
	}
	
	@After
	public void afterAllTestsHaveRun() throws IOException, CannotFindVpcException {	
		try {
			aws.rollbackTemplatesInFolder(EnvironmentSetupForTests.FOLDER_PATH, mainProjectAndEnv);
		} catch (InvalidParameterException e) {
			System.console().writer().write("Unable to properly rollback");
			e.printStackTrace();
		}
		aws.resetDeltaIndex(mainProjectAndEnv);
		Files.deleteIfExists(destFile);
	}

	@Test
	public void shouldCreateTheStacksRequiredOnly() throws WrongNumberOfStacksException, InterruptedException, FileNotFoundException, InvalidParameterException, IOException, CannotFindVpcException, StackCreateFailed {
		List<StackId> stackNames = aws.applyTemplatesFromFolder(EnvironmentSetupForTests.FOLDER_PATH, mainProjectAndEnv);
		
		assertEquals(expectedList.size(), stackNames.size());
		
		for(int i=0; i<expectedList.size(); i++) {
			StackId createdStackName = stackNames.get(i);
			assertEquals(expectedList.get(i), createdStackName);
			// TODO should just be a call to get current status because applyTemplatesFromFolder is a blocking call
			String status = monitor.waitForCreateFinished(createdStackName);
			assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		}
		
		// we are up to date, should not apply the files again
		stackNames = aws.applyTemplatesFromFolder(EnvironmentSetupForTests.FOLDER_PATH, mainProjectAndEnv);
		assertEquals(0, stackNames.size());
		
		// copy in extra files to dir
		FileUtils.copyFile(srcFile.toFile(), destFile.toFile());
		stackNames = aws.applyTemplatesFromFolder(EnvironmentSetupForTests.FOLDER_PATH, mainProjectAndEnv);
		assertEquals(1, stackNames.size());
		
		expectedList.add(proj+env+"03createRoutes");
		assertEquals(expectedList.get(2), stackNames.get(0));
		
		List<String> deletedStacks = aws.rollbackTemplatesInFolder(EnvironmentSetupForTests.FOLDER_PATH, mainProjectAndEnv);
		assertEquals(3, deletedStacks.size());
		assert(deletedStacks.containsAll(expectedList));
		
		int finalIndex = aws.getDeltaIndex(mainProjectAndEnv);
		assertEquals(0, finalIndex);
	}

	private void createExpectedNames() {
		expectedList.add(proj+env+"01createSubnet");
		expectedList.add(proj+env+"02createAcls");
	}

}

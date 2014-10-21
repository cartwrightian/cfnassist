package tw.com.integration;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import tw.com.EnvironmentSetupForTests;
import tw.com.providers.CloudFormationClient;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;

public class TestHaveValidTemplateFiles {

	private AWSCredentialsProvider credentialsProvider;
	
	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
	}
	
	@Test
	public void testAllTestCfnFilesAreValid() throws FileNotFoundException, IOException, InterruptedException {
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		CloudFormationClient cloudClient = new CloudFormationClient(cfnClient);
		File folder = new File("src/cfnScripts");
		
		assertTrue(folder.exists());	
		validateFolder(cloudClient, folder);
	}

	private void validateFolder(CloudFormationClient cloudClient, File folder)
			throws FileNotFoundException, IOException, InterruptedException {
		File[] files = folder.listFiles();
		for(File file : files) {
			if (file.isDirectory()) {
				validateFolder(cloudClient, file);		
			} else 
			{
				String contents = FileUtils.readFileToString(file, Charset.defaultCharset());
				cloudClient.validateTemplate(contents);
				
				Thread.sleep(200); // to avoid rate limit errors on AWS api
			}
		}
	}

}

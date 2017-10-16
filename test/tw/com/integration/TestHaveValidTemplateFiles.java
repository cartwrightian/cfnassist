package tw.com.integration;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.CloudFormationClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestHaveValidTemplateFiles {

	private AWSCredentialsProvider credentialsProvider;
	
	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
	}
	
	@Test
	public void testAllTestCfnFilesAreValid() throws IOException, InterruptedException {
		AmazonCloudFormation cfnClient = EnvironmentSetupForTests.createCFNClient();
		CloudFormationClient cloudClient = new CloudFormationClient(cfnClient);
		File folder = new File("src/cfnScripts");
		
		assertTrue(folder.exists());	
		validateFolder(cloudClient, folder);
	}

	private void validateFolder(CloudFormationClient cloudClient, File folder) throws InterruptedException
	{
		File[] files = folder.listFiles();
		for(File file : files) {
			if (file.isDirectory()) {
				validateFolder(cloudClient, file);		
			} else 
			{
				if (!file.isHidden()) {
                    try {
                        String contents = FileUtils.readFileToString(file, Charset.defaultCharset());
                        cloudClient.validateTemplate(contents);
                    } catch (IOException | AmazonServiceException e) {
                        fail(file.getAbsolutePath() + ": " + e);
                    }
                }
				
				Thread.sleep(200); // to avoid rate limit errors on AWS api
			}
		}
	}

}

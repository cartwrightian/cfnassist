package tw.com.integration;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.CFNClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestHaveValidTemplateFiles {

	@BeforeEach
	public void beforeTestsRun() {
	}
	
	@Test
	public void testAllTestCfnFilesAreValid() throws InterruptedException {
		software.amazon.awssdk.services.cloudformation.CloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient();
		CFNClient cloudClient = new CFNClient(cfnClient);
		File folder = new File("src/cfnScripts");
		
		assertTrue(folder.exists());	
		validateFolder(cloudClient, folder);
	}

	private void validateFolder(CFNClient cloudClient, File folder) throws InterruptedException
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
                    } catch (IOException | CloudFormationException e) {
                        fail(file.getAbsolutePath() + ": " + e);
                    }
                }
				
				Thread.sleep(200); // to avoid rate limit errors on AWS api
			}
		}
	}

}

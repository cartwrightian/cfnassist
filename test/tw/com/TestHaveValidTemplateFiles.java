package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

public class TestHaveValidTemplateFiles {

	private AWSCredentialsProvider credentialsProvider;
	
	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
	}
	

	@Test
	public void testAllTestCfnFilesAreValid() throws FileNotFoundException, IOException {
		AwsProvider facade = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
		File folder = new File("src/cfnScripts");
		
		assertTrue(folder.exists());	
		validateFolder(facade, folder);
	}


	private void validateFolder(AwsProvider facade, File folder)
			throws FileNotFoundException, IOException {
		File[] files = folder.listFiles();
		for(File file : files) {
			if (file.isDirectory()) {
				validateFolder(facade, file);
			} else
			{
				facade.validateTemplate(file);
			}
		}
	}

}

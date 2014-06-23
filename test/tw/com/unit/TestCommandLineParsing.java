package tw.com.unit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.commandline.Main;

public class TestCommandLineParsing {
	
	@Test
	public void shouldNotAllowBuildParameterWithDirAction() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-dir", FilesForTesting.ORDERED_SCRIPTS_FOLDER,
				"-build", "001"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testInvokeInitViaCommandLineMissingValue() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-init" 
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testMustGiveTheBuildNumberWhenUploadingArtifacts() {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SUBNET_WITH_PARAM,
				"-artifacts", uploads,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testMustGiveTheBucketWhenUploadingArtifacts() {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-file", FilesForTesting.SUBNET_WITH_PARAM,
				"-artifacts", uploads,
				"-build", "9987",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
	
	@Test
	public void testUploadArgumentParsing() {
		
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
				"-artifacts", uploads,
				"-build", "9987",
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-sns"
				};
		Main main = new Main(args);
		int result = main.parse(false);
		assertEquals(0, result);
	}
	
	@Test
	public void testUploadArgumentParsingFailsWithoutBucket() {
		
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
				"-artifacts", uploads,
				"-build", "9987",
				"-sns"
				};
		expectCommandLineFailureStatus(args);
	}
		
	@Test
	public void testCommandLineWithExtraIncorrectParams() {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-reset",
				"-parameters", "testA=123;testB"
				};
		expectCommandLineFailureStatus(args);
	}
	
	private void expectCommandLineFailureStatus(String[] args) {
		Main main = new Main(args);
		int result = main.parse();
		assertEquals(EnvironmentSetupForTests.FAILURE_STATUS, result);
	}

}

package tw.com;

public class CLIArgBuilder {

	public static String[] createSimpleStack(String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		return args;
	}
	
	public static String[] createSubnetStack(String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_STACK, 
				"-comment", testName
				};
		return args;
	}
	
	public static String[] updateSimpleStack(String testName, String sns) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_STACK_DELTA,
				sns, 
				"-comment", testName
				};
		return args;
	}
	
	public static String[] createSimpleStackWithSNS(String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-sns", 
				"-comment", testName
				};
		return args;
	}
	
	public static String[] createSimpleStackWithBuildNumber(String testName, String buildNumber) {
		String[] createArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SIMPLE_STACK,
				"-build", buildNumber,
				"-comment", testName
				};
		return createArgs;
	}

	public static String[] deleteSimpleStackWithBuildNumber(String testName, String buildNumber) {
		String[] deleteArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-delete", FilesForTesting.SIMPLE_STACK,
				"-build", buildNumber,
				"-comment", testName
				};
		return deleteArgs;
	}

	public static String[] deleteSimpleStack(String testName) {
		String[] deleteArgs = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-delete", FilesForTesting.SIMPLE_STACK,
				"-comment", testName
				};
		return deleteArgs;
	}
	
	public static String[] listStacks() {
		String[] args = { 
			"-env", EnvironmentSetupForTests.ENV, 
			"-project", EnvironmentSetupForTests.PROJECT, 
			"-region", EnvironmentSetupForTests.getRegion().toString(),
			"-ls"
			};
		return args;
	}

	public static String[] createSubnetStackWithParams(String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_WITH_PARAM,
				"-parameters", "zoneA=eu-west-1a",
				"-comment", testName
				};
		return args;
	}

	public static String[] deployFromDir(String orderedScriptsFolder,
			String sns, String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-dir", orderedScriptsFolder,
				"-comment", testName,
				sns
				};
		return args;
	}

	public static String[] rollbackFromDir(String orderedScriptsFolder,
			String sns, String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-rollback", orderedScriptsFolder,
				sns
				};
		return args;
	}

	public static String[] updateELB(String typeTag, String buildNumber, String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-build", buildNumber,
				"-elbUpdate", typeTag
				};
		return args;
	}

	public static String[] tidyNonLBAssociatedStacks(String testName) {
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT, 
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-tidyOldStakcs", FilesForTesting.SIMPLE_STACK
				};
		return args;
	}

	public static String[] createSubnetStackWithArtifactUpload(
			String buildNumber, String testName) {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);

		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
				"-artifacts", uploads,
				"-build", buildNumber,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-sns"
				};
		
		return args;
	}

	public static String[] uploadArtifacts(String buildNumber) {
		String artifacts = String.format("art1=%s;art2=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		
		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-s3create",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", buildNumber
				};
		return args;
	}

	public static String[] deleteArtifacts(String buildNumber, String filenameA, String filenameB) {
		String artifacts = String.format("art1=%s;art2=%s", filenameA, filenameB);

		String[] args = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-s3delete",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", buildNumber
				};
		return args;
	}



}

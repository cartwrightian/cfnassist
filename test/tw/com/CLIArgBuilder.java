package tw.com;

import com.amazonaws.services.cloudformation.model.StackStatus;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertTrue;

public class CLIArgBuilder {
	
	public static void checkForExpectedLine(String stackName, String project,
			String env, ByteArrayOutputStream stream) {
		String result = stream.toString();
		String lines[] = result.split("\\r?\\n");
		
		boolean found=false;
		for(String line : lines) {
			found = line.equals(String.format("%s\t%s\t%s\t%s",stackName, project, env, StackStatus.CREATE_COMPLETE.toString()));
			if (found) break;
		}
		assertTrue(found);
	}
	
	public static String[] createSimpleStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SIMPLE_STACK,
                "-comment", testName
                };
	}
	
	public static String[] createIAMStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-capabilityIAM",
                "-file", FilesForTesting.STACK_IAM_CAP,
                "-comment", testName
                };
	}
	
	public static String[] createSubnetStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SUBNET_STACK,
                "-comment", testName
                };
	}

	public static String[] createSubnetStackWithZones(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SIMPLE_STACK_WITH_AZ,
                "-comment", testName
        };
	}
	
	public static String[] updateSimpleStack(String testName, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SUBNET_STACK_DELTA,
                sns,
                "-comment", testName
                };
	}
	
	public static String[] createSimpleStackWithSNS(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SIMPLE_STACK,
                "-sns",
                "-comment", testName
                };
	}
	
	public static String[] createSimpleStackWithBuildNumber(String testName, String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SIMPLE_STACK,
                "-build", buildNumber,
                "-comment", testName
                };
	}

	public static String[] deleteSimpleStackWithBuildNumber(String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-delete", FilesForTesting.SIMPLE_STACK,
                "-build", buildNumber
                };
	}

    public static String[] deleteByNameSimpleStackWithBuildNumber(String name, String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-rm", name,
                "-build", buildNumber
        };
    }

	public static String[] deleteSimpleStack() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-delete", FilesForTesting.SIMPLE_STACK
                };
	}


    public static String[] deleteByNameSimpleStack(String name) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-rm", name
        };
    }
	
	public static String[] listStacks() {
        return new String[]{
            "-env", EnvironmentSetupForTests.ENV,
            "-project", EnvironmentSetupForTests.PROJECT,
            "-region", EnvironmentSetupForTests.getRegion().toString(),
            "-ls"
            };
	}
	

	public static String[] listInstances() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-instances"
                };
	}
	
	public static String[] createDiagrams(String folder) {
        return new String[]{
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-diagrams", folder
                };
	}

	public static String[] createSubnetStackWithParams(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SUBNET_WITH_PARAM,
                "-parameters", "zoneA=eu-west-1a",
                "-comment", testName
                };
	}

	public static String[] deployFromDir(String orderedScriptsFolder,
			String sns, String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-dir", orderedScriptsFolder,
                "-comment", testName,
                sns
                };
	}

	public static String[] rollbackFromDir(String orderedScriptsFolder, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-rollback", orderedScriptsFolder,
                sns
                };
	}
	
	public static String[] stepback(String orderedScriptsFolder, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-stepback", orderedScriptsFolder,
                sns
                };
	}

	public static String[] updateELB(String typeTag, Integer buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-build", buildNumber.toString(),
                "-elbUpdate", typeTag
                };
	}
	
	public static String[] whitelistCurrentIP(String type, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-whitelist", type, port.toString()
                };
	}
	
	public static String[] blacklistCurrentIP(String type, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-blacklist", type, port.toString()
                };
	}

	public static String[] tidyNonLBAssociatedStacks() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-tidyOldStacks", FilesForTesting.SIMPLE_STACK, "typeTag"
                };
	}

	public static String[] createSubnetStackWithArtifactUpload(Integer buildNumber, String testName) {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
                "-artifacts", uploads,
                "-build", buildNumber.toString(),
                "-bucket", EnvironmentSetupForTests.BUCKET_NAME,
                "-sns",
                "-comment", testName
                };
	}

	public static String[] uploadArtifacts(Integer buildNumber) {
		String artifacts = String.format("art1=%s;art2=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-s3create",
                "-artifacts", artifacts,
                "-bucket", EnvironmentSetupForTests.BUCKET_NAME,
                "-build", buildNumber.toString()
                };
	}

    public static String[] createKeyPair(String filename) {
        if (filename.isEmpty()) {
            return new String[] {
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-region", EnvironmentSetupForTests.getRegion().toString(),
                    "-keypair"
            };
        } else {
            return new String[]{
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-region", EnvironmentSetupForTests.getRegion().toString(),
                    "-keypair", filename
            };
        }
    }

	public static String[] deleteArtifacts(Integer buildNumber, String filenameA, String filenameB) {
		String artifacts = String.format("art1=%s;art2=%s", filenameA, filenameB);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-s3delete",
                "-artifacts", artifacts,
                "-bucket", EnvironmentSetupForTests.BUCKET_NAME,
                "-build", buildNumber.toString()
                };
	}

	public static String[] tagVPC(String tagName, String tagValue) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-tag", tagName, tagValue
        };
	}

    public static String[] initVPC(String env, String project, String vpcId) {
        return new String[]{
                "-env", env,
                "-project", project,
                "-region", EnvironmentSetupForTests.getRegion().toString(),
                "-init", vpcId
        };
    }

}

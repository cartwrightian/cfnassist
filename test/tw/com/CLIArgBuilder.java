package tw.com;

import com.amazonaws.services.cloudformation.model.StackStatus;

import static org.junit.Assert.assertTrue;

public class CLIArgBuilder {

    private static final String region = EnvironmentSetupForTests.getRegion().toString();

    public static void checkForExpectedLine(String stackName, String project,
                                            String env, String result) {
		//String result = stream.toString();
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
                "-region", region,
                "-file", FilesForTesting.SIMPLE_STACK,
                "-comment", testName
                };
	}
	
	public static String[] createIAMStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-capabilityIAM",
                "-file", FilesForTesting.STACK_IAM_CAP,
                "-comment", testName
                };
	}
	
	public static String[] createSubnetStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-file", FilesForTesting.SUBNET_STACK,
                "-comment", testName
                };
	}

	public static String[] createSubnetStackWithZones(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-file", FilesForTesting.SIMPLE_STACK_WITH_AZ,
                "-comment", testName
        };
	}
	
	public static String[] updateSimpleStack(String testName, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-file", FilesForTesting.SUBNET_STACK_DELTA,
                sns,
                "-comment", testName
                };
	}
	
	public static String[] createSimpleStackWithSNS(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-file", FilesForTesting.SIMPLE_STACK,
                "-sns",
                "-comment", testName
                };
	}
	
	public static String[] createSimpleStackWithBuildNumber(String testName, String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-file", FilesForTesting.SIMPLE_STACK,
                "-build", buildNumber,
                "-comment", testName
                };
	}

	public static String[] deleteSimpleStackWithBuildNumber(String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-delete", FilesForTesting.SIMPLE_STACK,
                "-build", buildNumber
                };
	}

    public static String[] deleteByNameSimpleStackWithBuildNumber(String name, String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-rm", name,
                "-build", buildNumber
        };
    }

	public static String[] deleteSimpleStack() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-delete", FilesForTesting.SIMPLE_STACK
                };
	}


    public static String[] deleteByNameSimpleStack(String name) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-rm", name
        };
    }
	
	public static String[] listStacks() {
        return new String[]{
            "-env", EnvironmentSetupForTests.ENV,
            "-project", EnvironmentSetupForTests.PROJECT,
            "-region", region,
            "-ls"
            };
	}
	

	public static String[] listInstances() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-instances"
                };
	}
	
	public static String[] createDiagrams(String folder) {
        return new String[]{
                "-region", region,
                "-diagrams", folder
                };
	}

	public static String[] createSubnetStackWithParams(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
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
                "-region", region,
                "-dir", orderedScriptsFolder,
                "-comment", testName,
                sns
                };
	}

    public static String[] purge(String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-purge",
                sns
        };
    }

    @Deprecated
	public static String[] stepback(String orderedScriptsFolder, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-stepback", orderedScriptsFolder,
                sns
                };
	}

    public static String[] back(String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-back",
                sns
        };
    }

	public static String[] updateELB(String typeTag, Integer buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-build", buildNumber.toString(),
                "-elbUpdate", typeTag
                };
	}
	
	public static String[] whitelistCurrentIP(String type, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-whitelist", type, port.toString()
                };
	}
	
	public static String[] blacklistCurrentIP(String type, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-blacklist", type, port.toString()
                };
	}

	public static String[] tidyNonLBAssociatedStacks() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
                "-tidyOldStacks", FilesForTesting.SIMPLE_STACK, "typeTag"
                };
	}

	public static String[] createSubnetStackWithArtifactUpload(Integer buildNumber, String testName) {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
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
                "-region", region,
                "-s3create",
                "-artifacts", artifacts,
                "-bucket", EnvironmentSetupForTests.BUCKET_NAME,
                "-build", buildNumber.toString()
                };
	}

    public static String[] createSSHCommand(String user) {
        if (user.isEmpty()) {
            return new String[] {
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-region", region,
                    "-ssh"
            };
        } else {
            return new String[] {
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-region", region,
                    "-ssh", user
            };
        }
    }

    public static String[] createKeyPair(String filename) {
        if (filename.isEmpty()) {
            return new String[] {
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-region", region,
                    "-keypair"
            };
        } else {
            return new String[]{
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-region", region,
                    "-keypair", filename
            };
        }
    }

	public static String[] deleteArtifacts(Integer buildNumber, String filenameA, String filenameB) {
		String artifacts = String.format("art1=%s;art2=%s", filenameA, filenameB);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-region", region,
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
                "-region", region,
                "-tag", tagName, tagValue
        };
	}

    public static String[] initVPC(String env, String project, String vpcId) {
        return new String[]{
                "-env", env,
                "-project", project,
                "-region", region,
                "-init", vpcId
        };
    }


}

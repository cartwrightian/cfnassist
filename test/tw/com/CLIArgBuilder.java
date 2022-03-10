package tw.com;


import static org.junit.Assert.assertTrue;

public class CLIArgBuilder {

    public static void checkForExpectedLine(String result, String expectedStackName, String expectedProject,
                                            String env, String... expectedStatus) {
		String lines[] = result.split("\\r?\\n");
		
		boolean start=false;
		boolean end = false;
		for(String line : lines) {
			start = line.startsWith(String.format("%s\t%s\t%s\t",expectedStackName, expectedProject, env));
            end = line.endsWith(createEnding(expectedStatus));
			if (start && end) break;
		}
		assertTrue(start && end);
	}

    private static String createEnding(String[] expectedStatus) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < expectedStatus.length; i++) {
            if (i>0) {
                builder.append("\t");
            }
            builder.append(expectedStatus[i]);
        }
        return builder.toString();
    }

    public static String[] createSimpleStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SIMPLE_STACK,
                "-comment", testName
                };
	}
	
	public static String[] createIAMStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-capabilityIAM",
                "-file", FilesForTesting.STACK_IAM_CAP,
                "-comment", testName
                };
	}
	
	public static String[] createSubnetStack(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SUBNET_STACK_JSON,
                "-comment", testName
                };
	}

	public static String[] createSubnetStackWithZones(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SIMPLE_STACK_WITH_AZ,
                "-comment", testName
        };
	}
	
	public static String[] updateSimpleStack(String testName, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SUBNET_STACK_DELTA,
                sns,
                "-comment", testName
                };
	}
	
	public static String[] createSimpleStackWithSNS(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SIMPLE_STACK,
                "-sns",
                "-comment", testName
                };
	}
	
	public static String[] createSimpleStackWithBuildNumber(String testName, String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SIMPLE_STACK,
                "-build", buildNumber,
                "-comment", testName
                };
	}

	public static String[] deleteSimpleStackWithBuildNumber(String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-delete", FilesForTesting.SIMPLE_STACK,
                "-build", buildNumber
                };
	}

    public static String[] deleteByNameSimpleStackWithBuildNumber(String name, String buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-rm", name,
                "-build", buildNumber
        };
    }

	public static String[] deleteSimpleStack() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-delete", FilesForTesting.SIMPLE_STACK
                };
	}


    public static String[] deleteByNameSimpleStack(String name) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-rm", name
        };
    }
	
	public static String[] listStacks() {
        return new String[]{
            "-env", EnvironmentSetupForTests.ENV,
            "-project", EnvironmentSetupForTests.PROJECT,
            "-ls"
            };
	}

    public static String[] listStackDrift() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-drift"
        };
    }
	

	public static String[] listInstances() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-instances"
                };
	}
	
	public static String[] createDiagrams(String folder) {
        return new String[]{
                "-diagrams", folder
                };
	}

	public static String[] createSubnetStackWithParams(String testName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
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
                "-dir", orderedScriptsFolder,
                "-comment", testName,
                sns
                };
	}

    public static String[] purge(String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-purge",
                sns
        };
    }

    @Deprecated
	public static String[] stepback(String orderedScriptsFolder, String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-stepback", orderedScriptsFolder,
                sns
                };
	}

    public static String[] back(String sns) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-back",
                sns
        };
    }

	public static String[] updateELB(String typeTag, Integer buildNumber) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-build", buildNumber.toString(),
                "-elbUpdate", typeTag
                };
	}
	
	public static String[] allowlistCurrentIP(String type, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-allowCurrentIP", type, port.toString()
                };
	}

    public static String[] allowHost(String type, String hostname, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-allowhost", type, hostname, port.toString()
        };
    }

    public static String[] blockHost(String type, String hostname, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-blockhost", type, hostname, port.toString()
        };
    }
	
	public static String[] blockCurrentIP(String type, Integer port) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-blockCurrentIP", type, port.toString()
                };
	}

	public static String[] tidyNonLBAssociatedStacks() {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-tidyOldStacks", FilesForTesting.SIMPLE_STACK, "typeTag"
                };
	}

	public static String[] createSubnetStackWithArtifactUpload(Integer buildNumber, String testName) {
		String uploads = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK_JSON);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
                "-artifacts", uploads,
                "-build", buildNumber.toString(),
                "-bucket", EnvironmentSetupForTests.BUCKET_NAME,
                "-sns",
                "-comment", testName
                };
	}

	public static String[] uploadArtifacts(Integer buildNumber) {
		String artifacts = String.format("art1=%s;art2=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK_JSON);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
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
                    "-ssh"
            };
        } else {
            return new String[] {
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-ssh", user
            };
        }
    }

    public static String[] createKeyPair(String filename) {
        if (filename.isEmpty()) {
            return new String[] {
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-keypair"
            };
        } else {
            return new String[]{
                    "-env", EnvironmentSetupForTests.ENV,
                    "-project", EnvironmentSetupForTests.PROJECT,
                    "-keypair", filename
            };
        }
    }

	public static String[] deleteArtifacts(Integer buildNumber, String filenameA, String filenameB) {
		String artifacts = String.format("art1=%s;art2=%s", filenameA, filenameB);

        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
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
                "-tag", tagName, tagValue
        };
	}

    public static String[] initVPC(String env, String project, String vpcId) {
        return new String[]{
                "-env", env,
                "-project", project,
                "-init", vpcId
        };
    }


    public static String[] tidyCloudWatch(Integer weeks) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-removeLogs", weeks.toString()
        };
    }

    public static String[] tagCloudWatchLog(String logGroupName) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-tagLog", logGroupName
        };
    }

    public static String[] getLogs(Integer days) {
        return new String[]{
                "-env", EnvironmentSetupForTests.ENV,
                "-project", EnvironmentSetupForTests.PROJECT,
                "-logs", days.toString()
        };
    }
}

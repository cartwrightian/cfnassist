package tw.com;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FilesForTesting {	
	// eclipse on windows may need you to set working directory to basedir and not workspace
	private static Path DIR = Paths.get("src","cfnScripts");
	
	public static final String SUBNET_STACK = DIR.resolve("subnet.json").toString();
	public static final String SUBNET_STACK_DELTA = DIR.resolve("subnet.delta.json").toString();
	public static final String SIMPLE_STACK = DIR.resolve("simpleStack.json").toString();
	public static final String SIMPLE_STACK_WITH_AZ = DIR.resolve("simpleStackWithAZ.json").toString();
	public static final String ACL = DIR.resolve("acl.json").toString();
	public static final String SUBNET_CIDR_PARAM = DIR.resolve("subnetWithCIDRParam.json").toString();
	public static final String SUBNET_WITH_PARAM = DIR.resolve("subnetWithParam.json").toString();
	public static final String SUBNET_WITH_S3_PARAM = DIR.resolve("subnetWithS3Param.json").toString();
	public static final String INSTANCE = DIR.resolve("instance.json").toString();
	public static final String ELB_AND_INSTANCE = DIR.resolve("elbAndInstance.json").toString();
	
	public static final String ORDERED_SCRIPTS_FOLDER = DIR.resolve("orderedScripts").toString();
	public static final Path ORDERED_SCRIPTS_WITH_UPDATES_FOLDER = DIR.resolve("orderedScriptsWithUpdate");
	public static final String STACK_UPDATE = ORDERED_SCRIPTS_WITH_UPDATES_FOLDER.resolve("02createSubnet.update.json").toString();

	public static final String STACK_IAM_CAP = DIR.resolve("simpleIAMStack.json").toString();

}

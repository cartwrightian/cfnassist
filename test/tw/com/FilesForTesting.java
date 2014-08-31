package tw.com;

public class FilesForTesting {	
	private static String DIR = "src/cfnScripts/";
	
	public static final String SUBNET_STACK = DIR + "subnet.json";
	public static final String SUBNET_STACK_DELTA = DIR + "subnet.delta.json";
	public static final String SIMPLE_STACK = DIR + "simpleStack.json";
	public static final String ACL = DIR + "acl.json";
	public static final String SUBNET_CIDR_PARAM = DIR + "subnetWithCIDRParam.json";
	public static final String SUBNET_WITH_PARAM = DIR + "subnetWithParam.json";
	public static final String SUBNET_WITH_S3_PARAM = DIR + "subnetWithS3Param.json";
	public static final String SUBNET_STACK_WITH_VPCTAG_PARAM = DIR + "subnetWithVPCTagParam.json";
	public static final String SUBNET_WITH_BUILD = DIR + "subnetWithBuild.json";
	public static final String ELB = DIR + "elb.json";
	public static final String INSTANCE = DIR + "instance.json";
	public static final String INSTANCE_WITH_TYPE = DIR + "instanceWithTypeTag.json";
	public static final String CAUSEROLLBACK = DIR + "causesRollBack.json";
	public static final String ELB_AND_INSTANCE = DIR + "elbAndInstance.json";
	
	public static final String ORDERED_SCRIPTS_FOLDER = DIR + "orderedScripts";
	public static final String ORDERED_SCRIPTS_WITH_DELTAS_FOLDER = DIR + "orderedScriptsWithDelta/";
	public static final String STACK_UPDATE = ORDERED_SCRIPTS_WITH_DELTAS_FOLDER + "02createSubnet.delta.json";

	

}

package tw.com.acceptance;

import static org.junit.Assert.*;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.ProjectAndEnv;
import tw.com.VpcRepository;
import tw.com.commandline.Main;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class TestCommandLineS3Operations {
	private static final String BUILD_NUMBER = "9987";
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonS3Client s3Client;
	
	@BeforeClass
	public static void beforeAllTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		s3Client = EnvironmentSetupForTests.createS3Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
	}

	private VpcRepository vpcRepository;
	private DeletesStacks deletesStacks;
	private ProjectAndEnv projectAndEnv;
	private static String filenameA = FilenameUtils.getName(FilesForTesting.ACL);
	private static String filenameB = FilenameUtils.getName(FilesForTesting.SUBNET_STACK);
	private static final String KEY_A = BUILD_NUMBER+"/"+filenameA;
	private static final String KEY_B = BUILD_NUMBER+"/"+filenameB;	
	
	@Before
	public void beforeEveryTestRun() {

		deleteTestKeysFromBucket();
		
		vpcRepository = new VpcRepository(ec2Client);
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		
		deletesStacks = new DeletesStacks(cfnClient);
		deletesStacks.ifPresent(EnvironmentSetupForTests.TEMPORARY_STACK)
			.ifPresent("CfnAssistTest01createSubnet")
			.ifPresent("CfnAssistTest02createAcls")
			.ifPresent("CfnAssistTestsimpleStack");
		deletesStacks.act();
		testName = test.getMethodName();
	}
	
	private void deleteTestKeysFromBucket() {
		try {
			s3Client.deleteObject(EnvironmentSetupForTests.BUCKET_NAME, KEY_A);
			s3Client.deleteObject(EnvironmentSetupForTests.BUCKET_NAME, KEY_B);
		} 
		catch(AmazonS3Exception exception) {
			System.out.println(exception);
		}	
	}

	@After
	public void afterEachTestIsRun() {
		deletesStacks.act();
		deleteTestKeysFromBucket();
	}
	
	@Rule public TestName test = new TestName();
	String testName = "";
	
	@Test
	public void testUploadArtifactsToS3AndAutopopulateAsParameters() {		
		String artifacts = String.format("urlA=%s;urlB=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] argsDeploy = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-file", FilesForTesting.SUBNET_WITH_S3_PARAM,
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER,
				"-sns"
				};
				
		Main main = new Main(argsDeploy);
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssist9987TestsubnetWithS3Param");
		
		assertEquals("deploy failed", 0, result);
		
		Vpc vpcId = vpcRepository.getCopyOfVpc(projectAndEnv);
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpcId);
		assertEquals(1, subnets.size());
		List<Tag> tags = subnets.get(0).getTags();

		List<Tag> expectedTags = new LinkedList<Tag>();
		expectedTags.add(new Tag().withKey("urlATag").withValue(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_A));
		expectedTags.add(new Tag().withKey("urlBTag").withValue(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_B));
		assertTrue(tags.containsAll(expectedTags));
	}
	
	@Test
	public void testUploadAndDeleteFileArtifacts() {		
		String artifacts = String.format("art1=%s;art2=%s", FilesForTesting.ACL, FilesForTesting.SUBNET_STACK);
		String[] argsS3create = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-s3create",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER,
				"-sns"
				};
				
		Main main = new Main(argsS3create);
		int result = main.parse();
		assertEquals("create failed", 0, result);
		
		List<S3ObjectSummary> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_B));
		
		artifacts = String.format("art1=%s;art2=%s", filenameA, filenameB);
		String[] argsS3Delete = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-s3delete",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER,
				"-sns"
				};
		
		main = new Main(argsS3Delete);
		result = main.parse();
		assertEquals("delete failed", 0, result);
	
		objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_B));
	}
	
	@Test
	public void testUploadAndDeleteFolderArtifact() {		
		String artifacts = String.format("folder=%s", FilesForTesting.ORDERED_SCRIPTS_FOLDER);
		String[] argsS3create = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-s3create",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER,
				"-sns"
				};
				
		Main main = new Main(argsS3create);
		int result = main.parse();
		assertEquals("create failed", 0, result);
		
		List<S3ObjectSummary> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		String key1 = BUILD_NUMBER+"/01createSubnet.json";
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, key1));
		String key2 = BUILD_NUMBER+"/02createAcls.json";
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, key2));
		
		artifacts = String.format("art1=%s;art2=%s", "01createSubnet.json", "02createAcls.json");
		String[] argsS3Delete = { 
				"-env", EnvironmentSetupForTests.ENV, 
				"-project", EnvironmentSetupForTests.PROJECT,
				"-region", EnvironmentSetupForTests.getRegion().toString(),
				"-s3delete",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER,
				"-sns"
				};
		
		main = new Main(argsS3Delete);
		result = main.parse();
		assertEquals("delete failed", 0, result);
	
		objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, key1));
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, key2));
	}
	
}

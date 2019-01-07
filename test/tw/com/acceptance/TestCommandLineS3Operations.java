package tw.com.acceptance;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.io.FilenameUtils;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import tw.com.CLIArgBuilder;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.commandline.Main;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.CloudClient;
import tw.com.repository.VpcRepository;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class TestCommandLineS3Operations {
	private static final Integer BUILD_NUMBER = 9987;
	private static Ec2Client ec2Client;
	private static AmazonCloudFormation cfnClient;
	private static AmazonS3 s3Client;
	
	@BeforeClass
	public static void beforeAllTestsRun() {
		ec2Client = EnvironmentSetupForTests.createEC2Client();
		s3Client = EnvironmentSetupForTests.createS3Client();
		cfnClient = EnvironmentSetupForTests.createCFNClient();
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
		
		vpcRepository = new VpcRepository(new CloudClient(ec2Client, new DefaultAwsRegionProviderChain()));
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
	
	@Rule
    public Timeout globalTimeout = new Timeout(100*1000);
	
	@Test
	public void testUploadArtifactsToS3AndAutopopulateAsParameters() {		
				
		Main main = new Main(CLIArgBuilder.createSubnetStackWithArtifactUpload(BUILD_NUMBER, testName));
		int result = main.parse();
		deletesStacks.ifPresent("CfnAssist9987TestsubnetWithS3Param");
		
		assertEquals("deploy failed", 0, result);
		
		Vpc vpcId = vpcRepository.getCopyOfVpc(projectAndEnv);
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpcId);
		assertEquals(1, subnets.size());
		List<Tag> tags = subnets.get(0).tags();

		List<Tag> expectedTags = new LinkedList<>();
		expectedTags.add(Tag.builder().key("urlATag").value(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_A).build());
		expectedTags.add(Tag.builder().key("urlBTag").value(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_B).build());
		assertTrue(tags.containsAll(expectedTags));
	}
	
	@Test
	public void testUploadAndDeleteFileArtifacts() {		
			
		Main main = new Main(CLIArgBuilder.uploadArtifacts(BUILD_NUMBER));
		int result = main.parse();
		assertEquals("create failed", 0, result);
		
		List<S3ObjectSummary> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_B));
		
		main = new Main(CLIArgBuilder.deleteArtifacts(BUILD_NUMBER, filenameA, filenameB));
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
				"-s3create",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER.toString()
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
				"-s3delete",
				"-artifacts", artifacts,
				"-bucket", EnvironmentSetupForTests.BUCKET_NAME,
				"-build", BUILD_NUMBER.toString()
				};
		
		main = new Main(argsS3Delete);
		result = main.parse();
		assertEquals("delete failed", 0, result);
	
		objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, key1));
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, key2));
	}
	
}

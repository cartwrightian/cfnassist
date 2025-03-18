package tw.com.unit;

import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.providers.CloudClient;
import tw.com.repository.VpcRepository;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class TestVpcRepository extends EasyMockSupport {
	
	private VpcRepository repository;
	private CloudClient cloudClient;
	private ProjectAndEnv projectAndEnv;
	
	@BeforeEach
	public void beforeEachTestIsRun() {	
		cloudClient = createMock(CloudClient.class);
		repository = new VpcRepository(cloudClient);
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	}

	private Vpc.Builder createVpc(String id) {
		return Vpc.builder().vpcId(id);
	}
	
	@Test
	public void shouldGetVpcByProjectAndEnvironmentTags() {
		List<Vpc> vpcs = new LinkedList<>();
	
		List<Tag> tags = EnvironmentSetupForTests.createExpectedEc2Tags(projectAndEnv,"");
		vpcs.add(createVpc("firstWrongId").build());
		vpcs.add(createVpc("correctId").tags(tags).build());
		List<Tag>  wrongTags = EnvironmentSetupForTests.createExpectedEc2Tags(EnvironmentSetupForTests.getAltProjectAndEnv(), "");
		vpcs.add(createVpc("wrongId").tags(wrongTags).build());
		EasyMock.expect(cloudClient.describeVpcs()).andReturn(vpcs);

		replayAll();
		Vpc result = repository.getCopyOfVpc(projectAndEnv);
		assertEquals("correctId", result.vpcId());
		verifyAll();
	}
	
	@Test
	public void shouldTestFindVpcAndThenDeleteATag() {
		List<Vpc> vpcs = new LinkedList<>();
		vpcs.add(createVpc("correctId").tags(EnvironmentSetupForTests.createExpectedEc2Tags(projectAndEnv,"")).build());
		List<String> resources = new LinkedList<>();
		resources.add("correctId");
		
		EasyMock.expect(cloudClient.describeVpcs()).andReturn(vpcs);	
		Tag tag = Tag.builder().key("TheKey").build();
		cloudClient.deleteTagsFromResources(resources, tag);
		
		replayAll();
		repository.deleteVpcTag(projectAndEnv, "TheKey");
		verifyAll();
	}
	
	@Test
	public void shouldGetVPCById() {		
		EasyMock.expect(cloudClient.describeVpc("correctId")).andReturn(createVpc("id").cidrBlock("cidrBlock").build());
		replayAll();
		Vpc result = repository.getCopyOfVpc("correctId");
		assertEquals("cidrBlock",result.cidrBlock());
		verifyAll();
	}
	
	@Test
	public void shouldMatchVpcAndFindDetlaIndexTag() throws CannotFindVpcException {
		List<Vpc> vpcs = new LinkedList<>();
		
		List<Tag> matchingTags = EnvironmentSetupForTests.createExpectedEc2Tags(projectAndEnv,"");
		matchingTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_DELTA","004422"));
		List<Tag>  wrongTags = EnvironmentSetupForTests.createExpectedEc2Tags(EnvironmentSetupForTests.getAltProjectAndEnv(), "");
		wrongTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_DELTA","005555"));
		
		vpcs.add(createVpc("correctId").tags(matchingTags).build());
		vpcs.add(createVpc("firstWrongId").tags(wrongTags).build());
		
		EasyMock.expect(cloudClient.describeVpcs()).andReturn(vpcs);
				
		replayAll();
		String result = repository.getVpcIndexTag(projectAndEnv);
		assertEquals("004422", result);
		verifyAll();
	}
	
	@Test
	public void shouldTestSomething() throws CannotFindVpcException {
		List<Vpc> vpcs = new LinkedList<>();
		
		List<Tag> matchingTags = EnvironmentSetupForTests.createExpectedEc2Tags(projectAndEnv,"");
		matchingTags.add(EnvironmentSetupForTests.createEc2Tag("tagName","correctValue"));
		List<Tag>  wrongTags = EnvironmentSetupForTests.createExpectedEc2Tags(EnvironmentSetupForTests.getAltProjectAndEnv(), "");
		wrongTags.add(EnvironmentSetupForTests.createEc2Tag("tagName","wrongValue"));
		
		vpcs.add(createVpc("correctId").tags(matchingTags).build());
		vpcs.add(createVpc("firstWrongId").tags(wrongTags).build());
		
		EasyMock.expect(cloudClient.describeVpcs()).andReturn(vpcs);
				
		replayAll();
		String result = repository.getVpcTag("tagName", projectAndEnv);
		assertEquals(result, "correctValue");
		verifyAll();
	}
	
	@Test
	public void shouldTestSomegthing() throws CannotFindVpcException {
		List<Tag> expectedTags = new LinkedList<>();
		expectedTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_DELTA", "0"));
		expectedTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_PROJECT", "CfnAssist"));
		expectedTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_ENV", "Test"));
			
		EasyMock.expect(cloudClient.describeVpc("vpcID11")).andReturn(createVpc("id").cidrBlock("cidrBlock").build());
		List<String> resources = new LinkedList<>();
		resources.add("vpcID11");
		cloudClient.addTagsToResources(resources, expectedTags);
		EasyMock.expectLastCall();
		
		replayAll();
		repository.initAllTags("vpcID11", projectAndEnv);
		verifyAll();
	}
	
	@Test
	public void shouldTestSetCfnDeltaIndex() throws CannotFindVpcException {
		List<Vpc> vpcs = new LinkedList<>();
		List<Tag> initialTags = EnvironmentSetupForTests.createExpectedEc2Tags(projectAndEnv,"");
		initialTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_DELTA","initialValue"));
		
		vpcs.add(createVpc("correctId").tags(initialTags).build());
		EasyMock.expect(cloudClient.describeVpcs()).andReturn(vpcs);
		
		List<String> resources = new LinkedList<>();
		resources.add("correctId");
		List<Tag> tags = new LinkedList<>();
		tags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_DELTA","newIndexValue"));
		cloudClient.addTagsToResources(resources, tags);
		EasyMock.expectLastCall();
		
		replayAll();
		repository.setVpcIndexTag(projectAndEnv, "newIndexValue");
		verifyAll();
	}
	
	@Test
	public void shouldTestSomethign() {
		List<Vpc> vpcs = new LinkedList<>();
		List<Tag> initialTags = EnvironmentSetupForTests.createExpectedEc2Tags(projectAndEnv,"");
		initialTags.add(EnvironmentSetupForTests.createEc2Tag("CFN_ASSIST_DELTA","initialValue"));
		
		vpcs.add(createVpc("correctId").tags(initialTags).build());
		EasyMock.expect(cloudClient.describeVpcs()).andReturn(vpcs);
		
		List<Tag> tags = new LinkedList<>();
		tags.add(EnvironmentSetupForTests.createEc2Tag("someKey","someValue"));
		List<String> resources = new LinkedList<>();
		resources.add("correctId");
		cloudClient.addTagsToResources(resources, tags);
		EasyMock.expectLastCall();

		replayAll();
		repository.setVpcTag(projectAndEnv, "someKey", "someValue");
		verifyAll();

	}

}

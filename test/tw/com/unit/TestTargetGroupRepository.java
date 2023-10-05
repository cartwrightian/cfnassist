package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Tag;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;
import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.exceptions.TooManyELBException;
import tw.com.providers.LoadBalancerClientV2;
import tw.com.repository.ResourceRepository;
import tw.com.repository.TargetGroupRepository;
import tw.com.repository.VpcRepository;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestTargetGroupRepository extends EasyMockSupport {
	
	private TargetGroupRepository elbRepository;
	private LoadBalancerClientV2 loadBalancerClient;
	private VpcRepository vpcRepository;
	private ResourceRepository cfnRepository;
	private final ProjectAndEnv projAndEnv = new ProjectAndEnv("proj", "testEnv");
	private final int port = 4242;

	@Before
	public void beforeEachTestRuns() {
		loadBalancerClient = createMock(LoadBalancerClientV2.class);
		vpcRepository  = createMock(VpcRepository.class);
		cfnRepository = createMock(ResourceRepository.class);	
		elbRepository = new TargetGroupRepository(loadBalancerClient, vpcRepository, cfnRepository);
	}
	
	@Test
	public void ShouldUseTagIfMoreThanOneTargetGroupMatches() throws TooManyELBException {
		String typeTag = "expectedType";
		String vpcId = "vpcId";

		List<Tag> lb1Tags = new LinkedList<>();
		lb1Tags.add(createTag(AwsFacade.TYPE_TAG, "someNonMatchingTag"));

		List<Tag> lb2Tags = new LinkedList<>();
		lb2Tags.add(createTag(AwsFacade.TYPE_TAG, typeTag));
		
		List<TargetGroup> targetGroups = new LinkedList<>();
		TargetGroup targetGroupA = createTargetGroup("lb1Name", vpcId);
		TargetGroup targetGroupB = createTargetGroup("lb2Name", vpcId);
		targetGroups.add(targetGroupA);
		targetGroups.add(targetGroupB);

		Vpc vpc = Vpc.builder().vpcId(vpcId).build();
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(loadBalancerClient.describerTargetGroups()).andReturn(targetGroups);
		EasyMock.expect(loadBalancerClient.getTagsFor(targetGroupA)).andReturn(lb1Tags);
		EasyMock.expect(loadBalancerClient.getTagsFor(targetGroupB)).andReturn(lb2Tags);
		
		replayAll();
		TargetGroup result = elbRepository.findTargetGroupFor(projAndEnv, typeTag);
		verifyAll();
		
		assertEquals("lb2Name", result.targetGroupName());
	}

	private TargetGroup createTargetGroup(String groupName, String vpcId) {
		// TODO likely need to populate more here
		return TargetGroup.builder().vpcId(vpcId).
				targetGroupName(groupName).build();
	}

	private Tag createTag(String key, String value) {
		return Tag.builder().key(key).value(value).build();
	}

	@Test
	public void ShouldThrowIfMoreThanOneELBAndNoMatchingTags() {
		List<Tag> tags = new LinkedList<>();
		tags.add(createTag("someOtherTag","someOtherValue"));
		String vpcId = "vpcId";

		List<TargetGroup> targetGroups = new LinkedList<>();
		TargetGroup targetGroupA = createTargetGroup("lb1Name", vpcId);
		TargetGroup targetGroupB = createTargetGroup("lb2Name", vpcId);
		targetGroups.add(targetGroupB);
		targetGroups.add(targetGroupA);

		Vpc vpc = Vpc.builder().vpcId(vpcId).build();
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(loadBalancerClient.describerTargetGroups()).andReturn(targetGroups);
		EasyMock.expect(loadBalancerClient.getTagsFor(targetGroupA)).andReturn(new LinkedList<>());
		EasyMock.expect(loadBalancerClient.getTagsFor(targetGroupB)).andReturn(tags);
		
		replayAll();
		try {
			elbRepository.findTargetGroupFor(projAndEnv,"notMatchingAnLB");
			fail("should have thrown");
		}
		catch(TooManyELBException expectedException) {
			// no op
		}
		verifyAll();
	}
	
	@Test
	public void shouldFetchELBsForTheVPC() throws TooManyELBException {
		List<TargetGroup> lbs = new LinkedList<>();
		lbs.add(TargetGroup.builder().targetGroupName("lb1Name").vpcId("someId").build());
		lbs.add(createTargetGroup("lb2Name", "vpcId"));

		Vpc vpc = Vpc.builder().vpcId("vpcId").build();
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(loadBalancerClient.describerTargetGroups()).andReturn(lbs);
		
		replayAll();
		TargetGroup result = elbRepository.findTargetGroupFor(projAndEnv, "ignoredWhenOnlyOneMatchingLB");
		assertEquals("lb2Name", result.targetGroupName());
		verifyAll();
	}
	
	@Test
	public void shouldRegisterELBs() throws CfnAssistException {
		String instanceIdA1 = "instanceA1";
		String instanceIdA2 = "instanceA2";
		String instanceIdB1 = "instanceB1";
		String instanceIdB2 = "instanceB2";

		List<software.amazon.awssdk.services.elasticloadbalancing.model.Instance> instancesInTheVPC = Arrays.asList(
				createInstance(instanceIdA1),
				createInstance(instanceIdA2),
				createInstance(instanceIdB1),
				createInstance(instanceIdB2));
		
		Set<String> instancesToAdd = new HashSet<>();
		instancesToAdd.add(instanceIdB1);
		instancesToAdd.add(instanceIdB2);
		
		Set<String> toRemove = new HashSet<>();
		toRemove.add(instanceIdA1);
		toRemove.add(instanceIdA2);
		
		String vpcId = "myVPC";
		Vpc vpc = Vpc.builder().vpcId(vpcId).build();

		Integer newBuildNumber = 11;
		projAndEnv.addBuildNumber(newBuildNumber);

		TargetGroup targetGroupA = TargetGroup.builder().targetGroupName("targetGroupA").vpcId(vpcId).build(); 
		TargetGroup targetGroupB = TargetGroup.builder().targetGroupName("targetGroupB").vpcId(vpcId).build();

		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(vpc);
		EasyMock.expect(loadBalancerClient.describerTargetGroups()).andReturn(Collections.singletonList(targetGroupA));
		EasyMock.expect(loadBalancerClient.getInstancesFor(targetGroupA)).andReturn(new HashSet<>(Arrays.asList(instanceIdA1, instanceIdA2)));
		EasyMock.expect(loadBalancerClient.getInstancesFor(targetGroupB)).andReturn(new HashSet<>(Arrays.asList(instanceIdA1, instanceIdA2, instanceIdB1, instanceIdB2)));

		SearchCriteria criteria = new SearchCriteria(projAndEnv);
		EasyMock.expect(cfnRepository.getAllInstancesMatchingType(criteria, "typeTag")).andReturn(instancesInTheVPC);

		// the new instances not present in target group one, so add them
		loadBalancerClient.registerInstances(targetGroupA, instancesToAdd, port);
		EasyMock.expectLastCall();

		EasyMock.expect(loadBalancerClient.describerTargetGroups()).andReturn(Collections.singletonList(targetGroupB));

		//EasyMock.expect(loadBalancerClient.deregisterInstances(targetGroupA, toRemove, port)).andReturn(instancesToAdd);
		EasyMock.expect(loadBalancerClient.deregisterInstances(targetGroupB, toRemove, port)).andReturn(instancesToAdd);


		replayAll();
		Set<String> result = elbRepository.updateInstancesMatchingBuild(projAndEnv, "typeTag", port);
		assertEquals(2, result.size());
		assertTrue(result.contains(instanceIdB1));
		assertTrue(result.contains(instanceIdB2));
		verifyAll();			
	}

//	private TargetGroup createElbDescriptionWithInstances(String vpcId, Instance... instances) {
//		return TargetGroup.builder().
//				vpcId(vpcId).target
//				instances(instances).
//				loadBalancerName("lbName").dnsName("dnsName").build();
//	}

	private Instance createInstance(String instanceId) {
		return Instance.builder().instanceId(instanceId).build();
	}

	@Test
	public void shouldGetInstancesForTheLB() throws CfnAssistException {
		String vpcId = "myVPC";
		String instanceId = "instanceA";
		//Instance insA = createInstance(instanceId); // associated
		
		List<TargetGroup> loadBalancerDescriptions = new LinkedList<>();
		TargetGroup targetGroup = TargetGroup.builder().vpcId(vpcId).build(); //createElbDescriptionWithInstances(vpcId, insA);
		loadBalancerDescriptions.add(targetGroup);

		Vpc vpc = Vpc.builder().vpcId(vpcId).build();

		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(vpc);
		EasyMock.expect(loadBalancerClient.describerTargetGroups()).andReturn(loadBalancerDescriptions);
		EasyMock.expect(loadBalancerClient.getInstancesFor(targetGroup)).andReturn(Collections.singleton(instanceId));
		
		replayAll();
		Set<String> result = elbRepository.findInstancesAssociatedWithTargetGroup(projAndEnv,"typeNotUsedWhenOneMatchingLB");
		verifyAll();
		
		assertEquals(1,  result.size());
		assertTrue(result.contains(instanceId));
		//assertEquals("instanceA", result.get(0).instanceId());
	}
	
	@Test
	public void shouldThrowIfNoBuildNumberIsGiven() throws CfnAssistException {
		replayAll();	
		try {
			elbRepository.updateInstancesMatchingBuild(projAndEnv, "typeTag", port);
			fail("should have thrown");
		}
		catch(MustHaveBuildNumber expectedException) {
			// no op
		}
		verifyAll();	
	}

}

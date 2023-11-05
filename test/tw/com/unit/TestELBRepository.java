package tw.com.unit;

import software.amazon.awssdk.services.ec2.model.Vpc;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.elasticloadbalancing.model.Tag;
import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.exceptions.TooManyELBException;
import tw.com.providers.LoadBalancerClassicClient;
import tw.com.repository.ELBRepository;
import tw.com.repository.ResourceRepository;
import tw.com.repository.VpcRepository;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(EasyMockRunner.class)
public class TestELBRepository extends EasyMockSupport {
	
	private ELBRepository elbRepository;
	private LoadBalancerClassicClient elbClient;
	private VpcRepository vpcRepository;
	private ResourceRepository cfnRepository;
	private final ProjectAndEnv projAndEnv = new ProjectAndEnv("proj", "testEnv");
	
	@Before
	public void beforeEachTestRuns() {
		elbClient = createMock(LoadBalancerClassicClient.class);
		vpcRepository  = createMock(VpcRepository.class);
		cfnRepository = createMock(ResourceRepository.class);	
		elbRepository = new ELBRepository(elbClient, vpcRepository, cfnRepository);
	}
	
	@Test
	public void ShouldUseTagIfMoreThanOneELB() throws TooManyELBException {
		String typeTag = "expectedType";
		
		List<Tag> lb1Tags = new LinkedList<>();
		lb1Tags.add(createTag(AwsFacade.TYPE_TAG, "someNonMatchingTag"));

		List<Tag> lb2Tags = new LinkedList<>();
		lb2Tags.add(createTag(AwsFacade.TYPE_TAG, typeTag));
		
		List<LoadBalancerDescription> lbs = new LinkedList<>();
		lbs.add(createELBDesc("lb1Name"));
		lbs.add(createELBDesc("lb2Name"));
		
		Vpc vpc = Vpc.builder().vpcId("vpcId").build();
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		EasyMock.expect(elbClient.getTagsFor("lb1Name")).andReturn(lb1Tags);
		EasyMock.expect(elbClient.getTagsFor("lb2Name")).andReturn(lb2Tags);
		
		replayAll();
		LoadBalancerDescription result = elbRepository.findELBFor(projAndEnv, typeTag);
		verifyAll();
		
		assertEquals("lb2Name", result.loadBalancerName());
	}

	private LoadBalancerDescription createELBDesc(String loadBalancerName) {
		return LoadBalancerDescription.builder().loadBalancerName(loadBalancerName).vpcId("vpcId").build();
	}

	private Tag createTag(String key, String value) {
		return Tag.builder().key(key).value(value).build();
	}

	@Test
	public void ShouldThrowIfMoreThanOneELBAndNoMatchingTags() {
		List<Tag> tags = new LinkedList<>();
		tags.add(createTag("someOtherTag","someOtherValue"));
		
		List<LoadBalancerDescription> lbs = new LinkedList<>();
		lbs.add(createELBDesc("lb1Name"));
		lbs.add(createELBDesc("lb2Name"));

		Vpc vpc = Vpc.builder().vpcId("vpcId").build();
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		EasyMock.expect(elbClient.getTagsFor("lb1Name")).andReturn(new LinkedList<>());
		EasyMock.expect(elbClient.getTagsFor("lb2Name")).andReturn(tags);
		
		replayAll();
		try {
			elbRepository.findELBFor(projAndEnv,"notMatchingAnLB");
			fail("should have thrown");
		}
		catch(TooManyELBException expectedException) {
			// no op
		}
		verifyAll();
	}
	
	@Test
	public void shouldFetchELBsForTheVPC() throws TooManyELBException {
		List<LoadBalancerDescription> lbs = new LinkedList<>();
		lbs.add(LoadBalancerDescription.builder().loadBalancerName("lb1Name").vpcId("someId").build());
		lbs.add(createELBDesc("lb2Name"));

		Vpc vpc = Vpc.builder().vpcId("vpcId").build();
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		
		replayAll();
		LoadBalancerDescription result = elbRepository.findELBFor(projAndEnv,"ignoredWhenOnlyOneMatchingLB");
		assertEquals("lb2Name", result.loadBalancerName());
		verifyAll();
	}
	
	@Test
	public void shouldRegisterELBs() throws CfnAssistException {			
		Instance insA1 = createInstance("instanceA1"); // initial
		Instance insA2 = createInstance("instanceA2"); // initial
		Instance insB1 = createInstance("instanceB1"); // new
		Instance insB2 = createInstance("instanceB2"); // new

		Set<Instance> instancesThatMatch = new HashSet<>(Arrays.asList(insA1, insA2, insB1, insB2));
//		instancesThatMatch.add(insA1);
//		instancesThatMatch.add(insA2);
//		instancesThatMatch.add(insB1);
//		instancesThatMatch.add(insB2);
		
		List<Instance> instancesToAdd = Arrays.asList(insB1, insB2);
//		instancesToAdd.add(insB1);
//		instancesToAdd.add(insB2);
		
		List<Instance> toRemove = Arrays.asList(insA1, insA2);
//		toRemove.add(insA1);
//		toRemove.add(insA2);
		
		String vpcId = "myVPC";
		Integer newBuildNumber = 11;
		projAndEnv.addBuildNumber(newBuildNumber);

		LoadBalancerDescription elbA = createElbDescriptionWithInstances(vpcId, insA1, insA2);
		LoadBalancerDescription elbB = createElbDescriptionWithInstances(vpcId, insA1, insA2, insB1, insB2);

		List<LoadBalancerDescription> initalLoadBalancers = new LinkedList<>();
		initalLoadBalancers.add(elbA);
		
		List<LoadBalancerDescription> updatedLoadBalancers = new LinkedList<>();
		updatedLoadBalancers.add(elbB);
		Vpc vpc = Vpc.builder().vpcId(vpcId).build();

		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(initalLoadBalancers);
		EasyMock.expect(elbClient.getInstancesFor(elbA)).andReturn(Arrays.asList(insA1, insA2));
		EasyMock.expect(elbClient.getInstancesFor(elbB)).andReturn(Arrays.asList(insA1, insA2, insB1, insB2));

		SearchCriteria criteria = new SearchCriteria(projAndEnv);
		EasyMock.expect(cfnRepository.getAllInstancesMatchingType(criteria, "typeTag")).andReturn(instancesThatMatch);

		elbClient.registerInstances(instancesToAdd, "lbName");
		EasyMock.expectLastCall();
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(updatedLoadBalancers);
		EasyMock.expect(elbClient.deregisterInstancesFromLB(toRemove, "lbName")).andReturn(instancesToAdd);
		
		replayAll();
		List<Instance> result = elbRepository.updateInstancesMatchingBuild(projAndEnv, "typeTag");
		assertEquals(2, result.size());
		assertEquals(insB1.instanceId(), result.get(0).instanceId());
		assertEquals(insB2.instanceId(), result.get(1).instanceId());
		verifyAll();			
	}

	private LoadBalancerDescription createElbDescriptionWithInstances(String vpcId, Instance... instances) {
		return LoadBalancerDescription.builder().
				vpcId(vpcId).
				instances(instances).
				loadBalancerName("lbName").dnsName("dnsName").build();
	}

	private Instance createInstance(String instanceId) {
		return Instance.builder().instanceId(instanceId).build();
	}

	@Test
	public void shouldGetInstancesForTheLB() throws TooManyELBException {
		String vpcId = "myVPC";
		Instance insA = createInstance("instanceA"); // associated
		
		List<LoadBalancerDescription> loadBalancerDescriptions = new LinkedList<>();
		LoadBalancerDescription elb = createElbDescriptionWithInstances(vpcId, insA);
		loadBalancerDescriptions.add(elb);

		Vpc vpc = Vpc.builder().vpcId(vpcId).build();

		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(loadBalancerDescriptions);
		EasyMock.expect(elbClient.getInstancesFor(elb)).andReturn(Collections.singletonList(insA));
		
		replayAll();
		List<Instance> result = elbRepository.findInstancesAssociatedWithLB(projAndEnv,"typeNotUsedWhenOneMatchingLB");
		verifyAll();
		
		assertEquals(1,  result.size());
		assertEquals("instanceA", result.get(0).instanceId());
	}

	@Test
	public void shouldGetNoInstancesIfELBNotFound() throws TooManyELBException {
		String vpcId = "myVPC";

		Vpc vpc = Vpc.builder().vpcId(vpcId).build();

		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(Collections.emptyList());

		replayAll();
		List<Instance> result = elbRepository.findInstancesAssociatedWithLB(projAndEnv,"typeNotUsedWhenOneMatchingLB");
		verifyAll();

		assertTrue(result.isEmpty());
	}
	
	@Test
	public void shouldThrowIfNoBuildNumberIsGiven() throws CfnAssistException {
		replayAll();	
		try {
			elbRepository.updateInstancesMatchingBuild(projAndEnv, "typeTag");
			fail("should have thrown");
		}
		catch(MustHaveBuildNumber expectedException) {
			// no op
		}
		verifyAll();	
	}

}

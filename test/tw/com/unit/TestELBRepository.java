package tw.com.unit;

import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.Tag;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.exceptions.TooManyELBException;
import tw.com.providers.LoadBalancerClient;
import tw.com.repository.ELBRepository;
import tw.com.repository.ResourceRepository;
import tw.com.repository.VpcRepository;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(EasyMockRunner.class)
public class TestELBRepository extends EasyMockSupport {
	
	private ELBRepository elbRepository;
	private LoadBalancerClient elbClient;
	private VpcRepository vpcRepository;
	private ResourceRepository cfnRepository;
	private ProjectAndEnv projAndEnv = new ProjectAndEnv("proj", "testEnv");
	
	@Before
	public void beforeEachTestRuns() {
		elbClient = createMock(LoadBalancerClient.class);
		vpcRepository  = createMock(VpcRepository.class);
		cfnRepository = createMock(ResourceRepository.class);	
		elbRepository = new ELBRepository(elbClient, vpcRepository, cfnRepository);
	}
	
	@Test
	public void ShouldUseTagIfMoreThanOneELB() throws TooManyELBException {
		String typeTag = "expectedType";
		
		List<Tag> lb1Tags = new LinkedList<>();
		lb1Tags.add(new Tag().withKey(AwsFacade.TYPE_TAG).withValue("someNonMatchingTag"));

		List<Tag> lb2Tags = new LinkedList<>();
		lb2Tags.add(new Tag().withKey(AwsFacade.TYPE_TAG).withValue(typeTag));
		
		List<LoadBalancerDescription> lbs = new LinkedList<>();
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb1Name").withVPCId("vpcId"));
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb2Name").withVPCId("vpcId"));
		
		Vpc vpc = new Vpc().withVpcId("vpcId");
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		EasyMock.expect(elbClient.getTagsFor("lb1Name")).andReturn(lb1Tags);
		EasyMock.expect(elbClient.getTagsFor("lb2Name")).andReturn(lb2Tags);
		
		replayAll();
		LoadBalancerDescription result = elbRepository.findELBFor(projAndEnv, typeTag);
		verifyAll();
		
		assertEquals("lb2Name", result.getLoadBalancerName());
	}
	
	@Test
	public void ShouldThrowIfMoreThanOneELBAndNoMatchingTags() {
		List<Tag> tags = new LinkedList<>();
		tags.add(new Tag().withKey("someOtherTag").withValue("someOtherValue"));
		
		List<LoadBalancerDescription> lbs = new LinkedList<>();
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb1Name").withVPCId("vpcId"));
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb2Name").withVPCId("vpcId"));
		
		Vpc vpc = new Vpc().withVpcId("vpcId");
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
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb1Name").withVPCId("someId"));
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb2Name").withVPCId("vpcId"));

		Vpc vpc = new Vpc().withVpcId("vpcId");
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		
		replayAll();
		LoadBalancerDescription result = elbRepository.findELBFor(projAndEnv,"ignoredWhenOnlyOneMatchingLB");
		assertEquals("lb2Name", result.getLoadBalancerName());
		verifyAll();
	}
	
	@Test
	public void shouldRegisterELBs() throws CfnAssistException {			
		Instance insA1 = new Instance().withInstanceId("instanceA1"); // initial
		Instance insA2 = new Instance().withInstanceId("instanceA2"); // initial
		Instance insB1 = new Instance().withInstanceId("instanceB1"); // new
		Instance insB2 = new Instance().withInstanceId("instanceB2"); // new

		List<Instance> instancesThatMatch = new LinkedList<>();
		instancesThatMatch.add(insA1);
		instancesThatMatch.add(insA2);
		instancesThatMatch.add(insB1);
		instancesThatMatch.add(insB2);
		
		List<Instance> instancesToAdd = new LinkedList<>();
		instancesToAdd.add(insB1);
		instancesToAdd.add(insB2);
		
		List<Instance> toRemove = new LinkedList<>();
		toRemove.add(insA1);
		toRemove.add(insA2);
		
		String vpcId = "myVPC";
		Integer newBuildNumber = 11;
		projAndEnv.addBuildNumber(newBuildNumber);

		List<LoadBalancerDescription> initalLoadBalancers = new LinkedList<>();
		initalLoadBalancers.add(new LoadBalancerDescription().withVPCId(vpcId).
				withInstances(insA1,insA2).
				withLoadBalancerName("lbName").withDNSName("dnsName"));
		
		List<LoadBalancerDescription> updatedLoadBalancers = new LinkedList<>();
		updatedLoadBalancers.add(new LoadBalancerDescription().withVPCId(vpcId).
				withInstances(insA1, insA2, insB1, insB2).
				withLoadBalancerName("lbName").withDNSName("dnsName"));
	
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(new Vpc().withVpcId(vpcId));
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(initalLoadBalancers);
		SearchCriteria criteria = new SearchCriteria(projAndEnv);
		EasyMock.expect(cfnRepository.getAllInstancesMatchingType(criteria, "typeTag")).andReturn(instancesThatMatch);

		elbClient.registerInstances(instancesToAdd, "lbName");
		EasyMock.expectLastCall();
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(updatedLoadBalancers);
		EasyMock.expect(elbClient.degisterInstancesFromLB(toRemove, "lbName")).andReturn(instancesToAdd);
		
		replayAll();
		List<Instance> result = elbRepository.updateInstancesMatchingBuild(projAndEnv, "typeTag");
		assertEquals(2, result.size());
		assertEquals(insB1.getInstanceId(), result.get(0).getInstanceId());
		assertEquals(insB2.getInstanceId(), result.get(1).getInstanceId());
		verifyAll();			
	}
	
	@Test
	public void shouldGetInstancesForTheLB() throws TooManyELBException {
		String vpcId = "myVPC";
		Instance insA = new Instance().withInstanceId("instanceA"); // associated
		
		List<LoadBalancerDescription> theLB = new LinkedList<>();
		theLB.add(new LoadBalancerDescription().withVPCId(vpcId).
				withInstances(insA).
				withLoadBalancerName("lbName").withDNSName("dnsName"));

		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(new Vpc().withVpcId(vpcId));
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(theLB);
		
		replayAll();
		List<Instance> result = elbRepository.findInstancesAssociatedWithLB(projAndEnv,"typeNotUsedWhenOneMatchingLB");
		verifyAll();
		
		assertEquals(1,  result.size());
		assertEquals("instanceA", result.get(0).getInstanceId());
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

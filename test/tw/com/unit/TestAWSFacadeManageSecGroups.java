package tw.com.unit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.providers.ProvidesCurrentIp;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

@RunWith(EasyMockRunner.class)
public class TestAWSFacadeManageSecGroups extends EasyMockSupport {
	
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private CloudFormRepository cfnRepository;
	private VpcRepository vpcRepository;
	private ELBRepository elbRepository;
	private MonitorStackEvents monitor;
	private CloudRepository cloudRepository;
	private ProvidesCurrentIp providesCurrentIp;
	private IdentityProvider identityProvider;
	

	@Before
	public void beforeEachTestRuns() {
		monitor = createMock(MonitorStackEvents.class);
		cfnRepository = createStrictMock(CloudFormRepository.class);
		vpcRepository = createStrictMock(VpcRepository.class);
		elbRepository = createStrictMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
		providesCurrentIp = createStrictMock(ProvidesCurrentIp.class);
		NotificationSender notificationSender = createStrictMock(NotificationSender.class);
		identityProvider = createStrictMock(IdentityProvider.class);
		
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider);
	}
	
	@Test
	public void testAddsIpAndPortToELBSecGroup() throws CfnAssistException, UnknownHostException {
		String type = "elbTypeTag";
		
		LoadBalancerDescription elbDescription = new LoadBalancerDescription().
				withLoadBalancerName("elbName").
				withDNSName("dNSName").withSecurityGroups("elbSecGroupId");
		
		Integer port = 8080;
		InetAddress address = Inet4Address.getByName("192.168.0.1");
		
		EasyMock.expect(elbRepository.findELBFor(projectAndEnv, type)).andReturn(elbDescription);
		cloudRepository.updateAddIpAndPortToSecGroup("elbSecGroupId", address, port);
		EasyMock.expectLastCall();
		EasyMock.expect(providesCurrentIp.getCurrentIp()).andReturn(address);
		
		replayAll();
		aws.whitelistCurrentIpForPortToElb(projectAndEnv, type, providesCurrentIp, port);
		verifyAll();
		
	}
	
	@Test
	public void testRemovesIpAndPortToELBSecGroup() throws CfnAssistException, UnknownHostException {
		String type = "elbTypeTag";
		
		LoadBalancerDescription elbDescription = new LoadBalancerDescription().
				withLoadBalancerName("elbName").
				withDNSName("dNSName").
				withSecurityGroups("elbSecGroupId");
		
		Integer port = 8090;
		InetAddress address = Inet4Address.getByName("192.168.0.2");
		
		EasyMock.expect(elbRepository.findELBFor(projectAndEnv, type)).andReturn(elbDescription);
		cloudRepository.updateRemoveIpAndPortFromSecGroup("elbSecGroupId", address, port);
		EasyMock.expectLastCall();
		EasyMock.expect(providesCurrentIp.getCurrentIp()).andReturn(address);
		
		replayAll();
		aws.blacklistCurrentIpForPortToElb(projectAndEnv, type, providesCurrentIp, port);
		verifyAll();		
	}
}

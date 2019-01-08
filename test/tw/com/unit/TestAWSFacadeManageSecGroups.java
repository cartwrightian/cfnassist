package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.providers.ProvidesCurrentIp;
import tw.com.repository.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@RunWith(EasyMockRunner.class)
public class TestAWSFacadeManageSecGroups extends EasyMockSupport {
	
	private AwsFacade aws;
	private ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
	private ELBRepository elbRepository;
	private CloudRepository cloudRepository;
	private ProvidesCurrentIp providesCurrentIp;
    private LoadBalancerDescription elbDescription;
    private Integer port = 8080;
    private InetAddress address;
    private String type = "elbTypeTag";
    private List<InetAddress> addresses;
	private String host = "nat.travisci.net";

	@Before
	public void beforeEachTestRuns() throws UnknownHostException {
		MonitorStackEvents monitor = createMock(MonitorStackEvents.class);
		CloudFormRepository cfnRepository = createStrictMock(CloudFormRepository.class);
		VpcRepository vpcRepository = createStrictMock(VpcRepository.class);
		elbRepository = createStrictMock(ELBRepository.class);
		cloudRepository =  createStrictMock(CloudRepository.class);
		providesCurrentIp = createStrictMock(ProvidesCurrentIp.class);
		NotificationSender notificationSender = createStrictMock(NotificationSender.class);
		IdentityProvider identityProvider = createStrictMock(IdentityProvider.class);
		LogRepository logRepository = createStrictMock(LogRepository.class);

		aws = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, cloudRepository, notificationSender, identityProvider, logRepository);

        elbDescription = LoadBalancerDescription.builder().
                loadBalancerName("elbName").
                dnsName("dNSName").securityGroups("elbSecGroupId").build();

        address = Inet4Address.getByName("192.168.0.1");
        addresses = new LinkedList<>();
        addresses.add(address);
	}
	
	@Test
	public void shouldAddCurrentIpAndPortToELBSecGroup() throws CfnAssistException {

		EasyMock.expect(elbRepository.findELBFor(projectAndEnv, type)).andReturn(elbDescription);
		cloudRepository.updateAddIpsAndPortToSecGroup("elbSecGroupId", addresses, port);
		EasyMock.expectLastCall();
		EasyMock.expect(providesCurrentIp.getCurrentIp()).andReturn(address);
		
		replayAll();
		aws.addCurrentIPWithPortToELB(projectAndEnv, type, providesCurrentIp, port);
		verifyAll();
	}
	
	@Test
	public void shouldRemoveCurrentIpAndPortFromELBSecGroup() throws CfnAssistException {

		EasyMock.expect(elbRepository.findELBFor(projectAndEnv, type)).andReturn(elbDescription);
		cloudRepository.updateRemoveIpsAndPortFromSecGroup("elbSecGroupId", addresses, port);
		EasyMock.expectLastCall();
		EasyMock.expect(providesCurrentIp.getCurrentIp()).andReturn(address);
		
		replayAll();
		aws.removeCurrentIPAndPortFromELB(projectAndEnv, type, providesCurrentIp, port);
		verifyAll();		
	}

	@Test
    public void shouldAddHostAndPortToELBSecGroup() throws UnknownHostException, CfnAssistException {

        List<InetAddress> addresses = Arrays.asList(Inet4Address.getAllByName(host));
        EasyMock.expect(elbRepository.findELBFor(projectAndEnv, type)).andReturn(elbDescription);

        cloudRepository.updateRemoveIpsAndPortFromSecGroup("elbSecGroupId", addresses, port);
        EasyMock.expectLastCall();

        replayAll();
        aws.removeHostAndPortFromELB(projectAndEnv, type, host, port);
        verifyAll();
    }


	@Test
	public void shouldRemoveHostAndPortFromELBSecGroup() throws UnknownHostException, CfnAssistException {

		List<InetAddress> addresses = Arrays.asList(Inet4Address.getAllByName(host));
		EasyMock.expect(elbRepository.findELBFor(projectAndEnv, type)).andReturn(elbDescription);

		cloudRepository.updateAddIpsAndPortToSecGroup("elbSecGroupId", addresses, port);
		EasyMock.expectLastCall();

		replayAll();
		aws.addHostAndPortToELB(projectAndEnv, type, host, port);
		verifyAll();
	}
}

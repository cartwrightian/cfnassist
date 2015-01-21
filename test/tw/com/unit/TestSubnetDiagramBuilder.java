package tw.com.unit;

import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.*;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;

@RunWith(EasyMockRunner.class)
public class TestSubnetDiagramBuilder extends EasyMockSupport {

	private NetworkChildDiagram networkDiagram;
	private tw.com.pictures.SecurityChildDiagram securityDiagram;
	private SubnetDiagramBuilder subnetDiagramBuilder;
	
	@Before
	public void beforeEachTestRuns() {
		networkDiagram = createStrictMock(NetworkChildDiagram.class);
		securityDiagram = createStrictMock(tw.com.pictures.SecurityChildDiagram.class);
		createStrictMock(VPCDiagramBuilder.class);
		Subnet subnet = new Subnet().withSubnetId("subnetId").withCidrBlock("cidrBlock");
		subnetDiagramBuilder = new SubnetDiagramBuilder(networkDiagram, securityDiagram, subnet);
	}

	@Test
	public void shouldAddInstanceToDiagram() throws CfnAssistException {
		Instance instance = new Instance().
				withInstanceId("instacneId").
				withPrivateIpAddress("privateIp").
				withTags(new Tag().withKey("Name").withValue("instanceName"));

		networkDiagram.addInstance("instacneId", "instanceName\n[instacneId]\n(privateIp)");
		securityDiagram.addInstance("instacneId", "instanceName\n[instacneId]\n(privateIp)");
		
		replayAll();
		subnetDiagramBuilder.add(instance);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTable() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().
				withRouteTableId("routeTableId").
				withTags(new Tag().withKey("Name").withValue("routeTableName"));;

		networkDiagram.addRouteTable("routeTableId", "routeTableName [routeTableId]");
		
		replayAll();
		subnetDiagramBuilder.addRouteTable(routeTable);
		verifyAll();
	}
	
	@Test
	public void shouldAddSecurityGroupToDiagram() throws CfnAssistException {
		SecurityGroup group = setupSecurityGroup();
		
		securityDiagram.addSecurityGroup("groupId","name [groupId]");
		
		replayAll();
		subnetDiagramBuilder.addSecurityGroup(group);
		verifyAll();	
	}
	
	@Test
	public void shouldAddSecurityGroupInboundPermsDiagram() throws CfnAssistException {
		SecurityGroup group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		group.withIpPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_in", "80-100");
		securityDiagram.connectWithLabel("groupId_tcp_80-100_in", "groupId", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupInboundPerms("groupId", ipPerms);
		verifyAll();	
	}
	
	@Test
	public void shouldAddSecurityGroupInboundPermsDiagramDedup() throws CfnAssistException {
		SecurityGroup group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		group.withIpPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_in", "80-100");
		securityDiagram.connectWithLabel("groupId_tcp_80-100_in", "groupId", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupInboundPerms("groupId", ipPerms);
		subnetDiagramBuilder.addSecGroupInboundPerms("groupId", ipPerms);
		verifyAll();	
	}

	@Test
	public void shouldAddOutboundIpPermissions() throws CfnAssistException {
		SecurityGroup group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		group.withIpPermissionsEgress(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_out", "80-100");
		securityDiagram.connectWithLabel("groupId", "groupId_tcp_80-100_out", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupOutboundPerms("groupId", ipPerms);
		verifyAll();	
	}
	
	@Test
	public void shouldAddOutboundIpPermissionsDedupConnections() throws CfnAssistException {
		SecurityGroup group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		group.withIpPermissionsEgress(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_out", "80-100");
		securityDiagram.connectWithLabel("groupId", "groupId_tcp_80-100_out", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupOutboundPerms("groupId", ipPerms);
		subnetDiagramBuilder.addSecGroupOutboundPerms("groupId", ipPerms);
		verifyAll();	
	}
	
	public static SecurityGroup setupSecurityGroup() {
		SecurityGroup group = new SecurityGroup().
				withGroupId("groupId").
				withGroupName("fullGroupName").
				withTags(VpcTestBuilder.CreateNameTag("name"));
		return group;
	}
	
	public static IpPermission setupIpPerms() {
		IpPermission ipPerms = new IpPermission().
				withFromPort(80).
				withToPort(100).
				withIpProtocol("tcp").
				withIpRanges("ipRanges");
		return ipPerms;
	}

}

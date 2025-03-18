package tw.com.unit;

import software.amazon.awssdk.services.ec2.model.*;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import tw.com.EnvironmentSetupForTests;
import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.*;

public class TestSubnetDiagramBuilder extends EasyMockSupport {

	private NetworkChildDiagram networkDiagram;
	private tw.com.pictures.SecurityChildDiagram securityDiagram;
	private SubnetDiagramBuilder subnetDiagramBuilder;
	
	@BeforeEach
	public void beforeEachTestRuns() {
		networkDiagram = createStrictMock(NetworkChildDiagram.class);
		securityDiagram = createStrictMock(tw.com.pictures.SecurityChildDiagram.class);
		createStrictMock(VPCDiagramBuilder.class);
		Subnet subnet = Subnet.builder().subnetId("subnetId").cidrBlock("cidrBlock").build();
		subnetDiagramBuilder = new SubnetDiagramBuilder(networkDiagram, securityDiagram, subnet);
	}

	@Test
	public void shouldAddInstanceToDiagram() throws CfnAssistException {
		Instance instance = Instance.builder().
				instanceId("instacneId").
				privateIpAddress("privateIp").
				tags(EnvironmentSetupForTests.createEc2Tag("Name","instanceName")).build();

		networkDiagram.addInstance("instacneId", "instanceName\n[instacneId]\n(privateIp)");
		securityDiagram.addInstance("instacneId", "instanceName\n[instacneId]\n(privateIp)");
		
		replayAll();
		subnetDiagramBuilder.add(instance);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTable() throws CfnAssistException {
		RouteTable routeTable = RouteTable.builder().
				routeTableId("routeTableId").
				tags(EnvironmentSetupForTests.createEc2Tag("Name","routeTableName")).build();

		networkDiagram.addRouteTable("subnetId_routeTableId", "routeTableName [routeTableId]");
		
		replayAll();
		subnetDiagramBuilder.addRouteTable(routeTable);
		verifyAll();
	}
	
	@Test
	public void shouldAddSecurityGroupToDiagram() throws CfnAssistException {
		SecurityGroup group = setupSecurityGroup().build();
		
		securityDiagram.addSecurityGroup("groupId","name [groupId]");
		
		replayAll();
		subnetDiagramBuilder.addSecurityGroup(group);
		verifyAll();	
	}
	
	@Test
	public void shouldAddSecurityGroupInboundPermsDiagram() throws CfnAssistException {
		//SecurityGroup group = setupSecurityGroup().build();
		IpPermission ipPerms = setupIpPerms();
		//group.ipPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_in", "80-100");
		securityDiagram.connectWithLabel("groupId_tcp_80-100_in", "groupId", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupInboundPerms("groupId", ipPerms);
		verifyAll();	
	}
	
	@Test
	public void shouldAddSecurityGroupInboundPermsDiagramDedup() throws CfnAssistException {
		//SecurityGroup group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		//group.withIpPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_in", "80-100");
		securityDiagram.connectWithLabel("groupId_tcp_80-100_in", "groupId", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupInboundPerms("groupId", ipPerms);
		subnetDiagramBuilder.addSecGroupInboundPerms("groupId", ipPerms);
		verifyAll();	
	}

	@Test
	public void shouldAddOutboundIpPermissions() throws CfnAssistException {
		//SecurityGroup.Builder group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		//group.ipPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_out", "80-100");
		securityDiagram.connectWithLabel("groupId", "groupId_tcp_80-100_out", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupOutboundPerms("groupId", ipPerms);
		verifyAll();	
	}
	
	@Test
	public void shouldAddOutboundIpPermissionsDedupConnections() throws CfnAssistException {
		//SecurityGroup.Builder group = setupSecurityGroup();
		IpPermission ipPerms = setupIpPerms();
		//group.ipPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_out", "80-100");
		securityDiagram.connectWithLabel("groupId", "groupId_tcp_80-100_out", "(ipRanges)\n[tcp]");
		
		replayAll();
		subnetDiagramBuilder.addSecGroupOutboundPerms("groupId", ipPerms);
		subnetDiagramBuilder.addSecGroupOutboundPerms("groupId", ipPerms);
		verifyAll();	
	}
	
	public static SecurityGroup.Builder setupSecurityGroup() {
		return SecurityGroup.builder().
				groupId("groupId").
				groupName("fullGroupName").
				tags(VpcTestBuilder.CreateNameTag("name"));
	}
	
	public static IpPermission setupIpPerms() {
		return IpPermission.builder().
				fromPort(80).
				toPort(100).
				ipProtocol("tcp").
				ipRanges(IpRange.builder().cidrIp("ipRanges").build()).build();
	}

}

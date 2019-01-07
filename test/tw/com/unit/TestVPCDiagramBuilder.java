package tw.com.unit;

import software.amazon.awssdk.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.EnvironmentSetupForTests;
import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.*;
import tw.com.pictures.dot.Recorder;

import java.io.IOException;

import static org.junit.Assert.assertSame;

@RunWith(EasyMockRunner.class)
public class TestVPCDiagramBuilder extends EasyMockSupport {
	private VPCDiagramBuilder builder;
	private Diagram networkDiagram;
	private ChildDiagram childDiagram;
	private Diagram securityDiagram;
	
	private Vpc vpc;
	private String vpcId = "theVpcId";
	private PortRange portRange = PortRange.builder().from(1023).to(1128).build();
	
	@Before
	public void beforeEachTestRuns() {
		vpc = Vpc.builder().vpcId(vpcId).build();
		networkDiagram = createStrictMock(Diagram.class);
		securityDiagram = createStrictMock(Diagram.class);
		builder = new VPCDiagramBuilder(vpc, networkDiagram, securityDiagram);
		childDiagram = createStrictMock(ChildDiagram.class);
	}
	
	@Test
	public void shouldCreateNetworkSubDiagramForClusters() throws CfnAssistException {
		Subnet subnet = Subnet.builder().
				subnetId("subnetId").
				tags(EnvironmentSetupForTests.createEc2Tag("Name","subnetName")).
				cidrBlock("cidrBlock").build();
		
		EasyMock.expect(networkDiagram.createSubDiagram("subnetId", "subnetName [subnetId]\n(cidrBlock)")).andReturn(childDiagram);
		
		replayAll();
		NetworkChildDiagram result = builder.createNetworkDiagramForSubnet(subnet);
		verifyAll();
		assertSame(childDiagram, result.getContained());
	}
	
	@Test
	public void shouldCreateSecuritySubDiagramForClusters() throws CfnAssistException {
		Subnet subnet = Subnet.builder().
				subnetId("subnetId").
				tags(EnvironmentSetupForTests.createEc2Tag("Name","subnetName")).
				cidrBlock("cidrBlock").build();
		
		EasyMock.expect(securityDiagram.createSubDiagram("subnetId", "subnetName [subnetId]\n(cidrBlock)")).andReturn(childDiagram);
		
		replayAll();
		tw.com.pictures.SecurityChildDiagram result = builder.createSecurityDiagramForSubnet(subnet);
		verifyAll();
		assertSame(childDiagram,result.getContained());
	}
	
	@Test
	public void shouldRenderDiagramsToRecorder() throws IOException {
		Recorder recorder = createStrictMock(Recorder.class);
		
		recorder.beginFor(vpc, "network_diagram");
		networkDiagram.addTitle(vpcId);
		networkDiagram.render(recorder);
		recorder.end();
		recorder.beginFor(vpc, "security_diagram");
		securityDiagram.addTitle(vpcId);
		securityDiagram.render(recorder);
		recorder.end();
	
		replayAll();
		builder.render(recorder);
		verifyAll();
	}
	
	@Test
	public void shouldAddEIP() throws CfnAssistException {
		Address eip = Address.builder().publicIp("publicIP").allocationId("allocId").build();
		networkDiagram.addPublicIPAddress("publicIP", "publicIP [allocId]");

		replayAll();
		builder.addEIP(eip);
		verifyAll();
	}
	
	@Test
	public void shouldAddDB() throws CfnAssistException {
		DBInstance rds = new DBInstance().withDBName("dbName").withDBInstanceIdentifier("instanceID");
		networkDiagram.addDBInstance("instanceID", "dbName [instanceID]");
		securityDiagram.addDBInstance("instanceID", "dbName [instanceID]");

		replayAll();
		builder.addDBInstance(rds);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateDBWithSubent() throws CfnAssistException {
		DBInstance rds = new DBInstance().withDBName("dbName").withDBInstanceIdentifier("instanceID");

		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		networkDiagram.associateWithSubDiagram("instanceID", "subnetId", subnetDiagramBuilder);
		securityDiagram.associateWithSubDiagram("instanceID", "subnetId", subnetDiagramBuilder);
	
		replayAll();
		builder.associateDBWithSubnet(rds, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddELB() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");
		networkDiagram.addLoadBalancer("dnsName", "lbName");
		securityDiagram.addLoadBalancer("dnsName", "lbName");
		
		replayAll();
		builder.addELB(elb);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateELBWithSubnet() {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		networkDiagram.associateWithSubDiagram("dnsName", "subnetId", subnetDiagramBuilder);
		securityDiagram.associateWithSubDiagram("dnsName", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.associateELBToSubnet(elb, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAssociateELBWithInstance() {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		networkDiagram.addConnectionBetween("dnsName", "instanceId");
		
		replayAll();
		builder.associateELBToInstance(elb, "instanceId");
		verifyAll();
	}
	
	@Test
	public void shouldAddLocalRoute() throws CfnAssistException {
		Route route = createRoute("192.168.0.22/32", "local");

		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.associateWithSubDiagram("192.168.0.22/32", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("routeTableId", "subnetId", route);
		verifyAll();
	}

	private Route createRoute(String cidrBlock, String gatewayId) {
		return Route.builder().gatewayId(gatewayId).
					destinationCidrBlock(cidrBlock).
					state(RouteState.ACTIVE).build();
	}

	@Test
	public void shouldAddDefaultRoute() throws CfnAssistException {
		Route route = createRoute("0.0.0.0/0", "local");

		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.associateWithSubDiagram("0.0.0.0/0", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("routeTableId", "subnetId", route);
		verifyAll();
	}
	
	// Is this a real possibility?
	@Test
	public void shouldAddRouteCidrMissing() throws CfnAssistException {
		Route route = Route.builder().gatewayId("local").
				state(RouteState.ACTIVE).build();

		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.associateWithSubDiagram("no cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("routeTableId", "subnetId", route);
		verifyAll();
	}

	
	@Test
	public void shouldAddNonLocalRouteWithGateway() throws CfnAssistException {
		Route route = createRoute("192.168.0.22/32", "gatewayId");

		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.addRouteToInstance("gatewayId", "subnetId_routeTableId", subnetDiagramBuilder, "192.168.0.22/32");
		
		replayAll();
		builder.addRoute("routeTableId","subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddNonLocalRouteWithInstance() throws CfnAssistException {
		Route route = Route.builder().
				instanceId("targetInstance").
				destinationCidrBlock("192.168.0.22/32").
				state(RouteState.ACTIVE).build();
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.addRouteToInstance("targetInstance", "subnetId_routeTableId", subnetDiagramBuilder, "192.168.0.22/32");
		
		replayAll();
		builder.addRoute("routeTableId","subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddNonLocalRouteWithBlackhole() throws CfnAssistException {
		Route route = Route.builder().
				destinationCidrBlock("192.168.0.22/32").
				state(RouteState.BLACKHOLE).build();
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		networkDiagram.addConnectionFromSubDiagram("blackhole", "subnetId", subnetDiagramBuilder, "192.168.0.22/32");
		
		replayAll();
		builder.addRoute("routeTableId","subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithSubnet() throws CfnAssistException {
		RouteTable routeTable = RouteTable.builder().routeTableId("rtId").
				tags(VpcTestBuilder.CreateNameTag("rtName")).build();
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		subnetDiagramBuilder.addRouteTable(routeTable);
	
		replayAll();
		builder.addAsssociatedRouteTable(routeTable, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddACLs() throws CfnAssistException {
		NetworkAcl acl = NetworkAcl.builder().networkAclId("networkAclId").
				tags(VpcTestBuilder.CreateNameTag("ACL")).build();
		securityDiagram.addACL("networkAclId","ACL [networkAclId]");

		replayAll();
		builder.addAcl(acl);
		verifyAll();
	}
	
	@Test
	public void shouldAddAssociateACLWithSubnet() {
		NetworkAcl acl = NetworkAcl.builder().networkAclId("networkAclId").
				tags(VpcTestBuilder.CreateNameTag("ACL")).build();
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		securityDiagram.associateWithSubDiagram("networkAclId", "subnetId", subnetDiagramBuilder);

		replayAll();
		builder.associateAclWithSubnet(acl, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "cidrBlock", true, 42, RuleAction.ALLOW);
		
		securityDiagram.addCidr("out_cidrBlock_aclId","cidrBlock");
		securityDiagram.addConnectionFromSubDiagram("out_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addACLOutbound("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllCidrAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0", true, 42, RuleAction.ALLOW);
		
		securityDiagram.addCidr("out_0.0.0.0/0_aclId","any");
		securityDiagram.addConnectionFromSubDiagram("out_0.0.0.0/0_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addACLOutbound("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllCidrBlocked() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0", true, 42, RuleAction.DENY);
		
		securityDiagram.addCidr("out_0.0.0.0/0_aclId","any");
		securityDiagram.addBlockedConnectionFromSubDiagram("out_0.0.0.0/0_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addACLOutbound("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryNoPortRangeAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry entry = createAclEntry(null, "cidrBlock", true, 42, RuleAction.ALLOW);
		
		securityDiagram.addCidr("out_cidrBlock_aclId","cidrBlock");
		securityDiagram.addConnectionFromSubDiagram("out_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[all]\n(rule:42)");
		
		replayAll();
		builder.addACLOutbound("aclId", entry, "subnetId");
		verifyAll();
	}

	@Test
	public void shouldAddInboundAclEntryAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 42, RuleAction.ALLOW);
		
		securityDiagram.addCidr("in_cidrBlock_aclId", "cidrBlock");
		securityDiagram.addConnectionToSubDiagram("in_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addACLInbound("aclId", entry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddInboundAclEntryAllowedDefaultRule() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 32767, RuleAction.ALLOW);
		
		securityDiagram.addCidr("in_cidrBlock_aclId", "cidrBlock");
		securityDiagram.addConnectionToSubDiagram("in_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:default)");
		
		replayAll();
		builder.addACLInbound("aclId", entry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddInboundAclEntryAllowedSameSinglePort() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry entry = createAclEntry(PortRange.builder().from(80).to(80).build(), "cidrBlock", false, 42, RuleAction.ALLOW);
		
		securityDiagram.addCidr("in_cidrBlock_aclId", "cidrBlock");
		securityDiagram.addConnectionToSubDiagram("in_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[80]\n(rule:42)");
		
		replayAll();
		builder.addACLInbound("aclId", entry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddInboundAclEntryBlocked() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 42, RuleAction.DENY);
		
		securityDiagram.addCidr("in_cidrBlock_aclId", "cidrBlock");
		securityDiagram.addBlockedConnectionToSubDiagram("in_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addACLInbound("aclId", entry, "subnetId");
		verifyAll();
	}
		
	@Test
	public void shouldAddSecurityGroupWithinSubnet() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		SecurityGroup group = SecurityGroup.builder().groupId("groupId").build();
		
		subnetDiagramBuilder.addSecurityGroup(group);
		
		replayAll();
		builder.addSecurityGroup(group, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddSecurityGroup() throws CfnAssistException {

		SecurityGroup group = SecurityGroup.builder().groupId("groupId").groupName("name").build();
		
		securityDiagram.addSecurityGroup("groupId","name [groupId]");
		
		replayAll();
		builder.addSecurityGroup(group);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateSecurityGroupAndInstance() {
		SecurityGroup group = SecurityGroup.builder().groupId("groupId").build();
		
		securityDiagram.associate("instanceId", "groupId");
		
		replayAll();
		builder.associateInstanceWithSecGroup("instanceId", group);
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsInboundWithSubnet() throws CfnAssistException {
		IpPermission perms = IpPermission.builder().fromPort(80).build();

		SubnetDiagramBuilder subnetDiaBuilder = setupSubnetDiagramBuidler();
		subnetDiaBuilder.addSecGroupInboundPerms("groupId", perms);
		
		replayAll();
		builder.addSecGroupInboundPerms("groupId", perms, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsInbound() throws CfnAssistException {
		//SecurityGroup.Builder group = TestSubnetDiagramBuilder.setupSecurityGroup();
		IpPermission ipPerms = TestSubnetDiagramBuilder.setupIpPerms();
		//group.ipPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_in", "80-100");
		securityDiagram.connectWithLabel("groupId_tcp_80-100_in", "groupId", "(ipRanges)\n[tcp]");
		
		replayAll();
		builder.addSecGroupInboundPerms("groupId", ipPerms);
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsOutboundWithSubnet() throws CfnAssistException {
		IpPermission perms = IpPermission.builder().fromPort(80).build();

		SubnetDiagramBuilder subnetDiaBuilder = setupSubnetDiagramBuidler();
		subnetDiaBuilder.addSecGroupOutboundPerms("groupId", perms);
		
		replayAll();
		builder.addSecGroupOutboundPerms("groupId", perms, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsOutbound() throws CfnAssistException {
		//SecurityGroup.Builder group = TestSubnetDiagramBuilder.setupSecurityGroup();
		IpPermission ipPerms = TestSubnetDiagramBuilder.setupIpPerms();
		//group.ipPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_out", "80-100");
		securityDiagram.connectWithLabel("groupId", "groupId_tcp_80-100_out", "(ipRanges)\n[tcp]");
		
		replayAll();
		builder.addSecGroupOutboundPerms("groupId", ipPerms);
		verifyAll();
	}
	
	@Test
	public void shouldSecGroup() throws CfnAssistException {
		SecurityGroup secGroup = SecurityGroup.builder().groupId("groupId").groupName("groupName").build();
		
		securityDiagram.addSecurityGroup("groupId", "groupName [groupId]");
		
		replayAll();
		builder.addSecurityGroup(secGroup);
		verifyAll();
	}

	private NetworkAclEntry createAclEntry(PortRange thePortRange, String cidrBlock, Boolean outbound, Integer ruleNumber, 
			RuleAction ruleAction) {
		return NetworkAclEntry.builder().
				cidrBlock(cidrBlock).
				egress(outbound).
				portRange(thePortRange).
				protocol("6").
				ruleAction(ruleAction).
				ruleNumber(ruleNumber).build();
	}
	
	private SubnetDiagramBuilder setupSubnetDiagramBuidler() {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		return subnetDiagramBuilder;
	}
}

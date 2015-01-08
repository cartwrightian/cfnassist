package tw.com.unit;

import static org.junit.Assert.*;

import java.io.IOException;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.PortRange;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RuleAction;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;
import tw.com.pictures.Diagram;
import tw.com.pictures.NetworkChildDiagram;
import tw.com.pictures.SubnetDiagramBuilder;
import tw.com.pictures.VPCDiagramBuilder;
import tw.com.pictures.dot.Recorder;

@RunWith(EasyMockRunner.class)
public class TestVPCDiagramBuilder extends EasyMockSupport {
	private VPCDiagramBuilder builder;
	private Diagram networkDiagram;
	private ChildDiagram childDiagram;
	private Diagram securityDiagram;
	
	private Vpc vpc;
	private String vpcId = "theVpcId";
	private PortRange portRange = new PortRange().withFrom(1023).withTo(1128);
	
	@Before
	public void beforeEachTestRuns() {
		vpc = new Vpc().withVpcId(vpcId);
		networkDiagram = createStrictMock(Diagram.class);
		securityDiagram = createStrictMock(Diagram.class);
		builder = new VPCDiagramBuilder(vpc, networkDiagram, securityDiagram);
		childDiagram = createStrictMock(ChildDiagram.class);
	}
	
	@Test
	public void shouldCreateNetworkSubDiagramForClusters() throws CfnAssistException {
		Subnet subnet = new Subnet().
				withSubnetId("subnetId").
				withTags(new Tag().withKey("Name").withValue("subnetName")).
				withCidrBlock("cidrBlock");
		
		EasyMock.expect(networkDiagram.createSubDiagram("subnetId", "subnetName [subnetId]\n(cidrBlock)")).andReturn(childDiagram);
		
		replayAll();
		NetworkChildDiagram result = builder.createNetworkDiagramForSubnet(subnet);
		verifyAll();
		assertSame(childDiagram, result.getContained());
	}
	
	@Test
	public void shouldCreateSecuritySubDiagramForClusters() throws CfnAssistException {
		Subnet subnet = new Subnet().
				withSubnetId("subnetId").
				withTags(new Tag().withKey("Name").withValue("subnetName")).
				withCidrBlock("cidrBlock");
		
		EasyMock.expect(securityDiagram.createSubDiagram("subnetId", "subnetName [subnetId]\n(cidrBlock)")).andReturn(childDiagram);
		
		replayAll();
		SecurityChildDiagram result = builder.createSecurityDiagramForSubnet(subnet);
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
		Address eip = new Address().withPublicIp("publicIP").withAllocationId("allocId");
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
	public void shouldAssociateELBWithSubnet() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		networkDiagram.associateWithSubDiagram("dnsName", "subnetId", subnetDiagramBuilder);
		securityDiagram.associateWithSubDiagram("dnsName", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.associateELBToSubnet(elb, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAssociateELBWithInstance() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		networkDiagram.addConnectionBetween("dnsName", "instanceId");
		
		replayAll();
		builder.associateELBToInstance(elb, "instanceId");
		verifyAll();
	}
	
	@Test
	public void shouldAddLocalRoute() throws CfnAssistException {
		Route route = new Route().withGatewayId("local").withDestinationCidrBlock("cidr");
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.associateWithSubDiagram("cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddLocalRouteNoCIDR() throws CfnAssistException {
		Route route = new Route().withGatewayId("local");
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.associateWithSubDiagram("no cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddNonLocalRoute() throws CfnAssistException {
		Route route = new Route().withGatewayId("gatewayId").withDestinationCidrBlock("cidr");
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		networkDiagram.addConnectionFromSubDiagram("gatewayId", "subnetId", subnetDiagramBuilder, "cidr");
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithSubnet() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").
				withTags(VpcTestBuilder.CreateNameTag("rtName"));
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		subnetDiagramBuilder.addRouteTable(routeTable);
	
		replayAll();
		builder.addRouteTable(routeTable, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithoutSubnet() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").
				withTags(VpcTestBuilder.CreateNameTag("rtName"));
		
		networkDiagram.addRouteTable("rtId", "rtName [rtId]");
	
		replayAll();
		builder.addRouteTable(routeTable, null);
		verifyAll();
	}
	
	@Test
	public void shouldAddACLs() throws CfnAssistException {
		NetworkAcl acl = new NetworkAcl().withNetworkAclId("networkAclId").
				withTags(VpcTestBuilder.CreateNameTag("ACL"));
		securityDiagram.addACL("networkAclId","ACL [networkAclId]");

		replayAll();
		builder.addAcl(acl);
		verifyAll();
	}
	
	@Test
	public void shouldAddAssociateACLWithSubnet() throws CfnAssistException {
		NetworkAcl acl = new NetworkAcl().withNetworkAclId("networkAclId").
				withTags(VpcTestBuilder.CreateNameTag("ACL"));
		
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();

		securityDiagram.associateWithSubDiagram("networkAclId", "subnetId", subnetDiagramBuilder);

		replayAll();
		builder.associateAclWithSubnet(acl, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = setupSubnetDiagramBuidler();
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "cidrBlock", true, 42, RuleAction.Allow);
		
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
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0", true, 42, RuleAction.Allow);
		
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
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0", true, 42, RuleAction.Deny);
		
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
		
		NetworkAclEntry entry = createAclEntry(null, "cidrBlock", true, 42, RuleAction.Allow);
		
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
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 42, RuleAction.Allow);
		
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
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 32767, RuleAction.Allow);
		
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
		
		NetworkAclEntry entry = createAclEntry(new PortRange().withFrom(80).withTo(80), "cidrBlock", false, 42, RuleAction.Allow);
		
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
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 42, RuleAction.Deny);
		
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

		SecurityGroup group = new SecurityGroup().withGroupId("groupId");
		
		subnetDiagramBuilder.addSecurityGroup(group);
		
		replayAll();
		builder.addSecurityGroup(group, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddSecurityGroup() throws CfnAssistException {

		SecurityGroup group = new SecurityGroup().withGroupId("groupId").withGroupName("name");
		
		securityDiagram.addSecurityGroup("groupId","name [groupId]");
		
		replayAll();
		builder.addSecurityGroup(group);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateSecurityGroupAndInstance() throws CfnAssistException {
		SecurityGroup group = new SecurityGroup().withGroupId("groupId");
		
		securityDiagram.associate("instanceId", "groupId");
		
		replayAll();
		builder.associateInstanceWithSecGroup("instanceId", group);
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsInboundWithSubnet() throws CfnAssistException {
		IpPermission perms = new IpPermission().withFromPort(80);

		SubnetDiagramBuilder subnetDiaBuilder = setupSubnetDiagramBuidler();
		subnetDiaBuilder.addSecGroupInboundPerms("groupId", perms);
		
		replayAll();
		builder.addSecGroupInboundPerms("groupId", perms, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsInbound() throws CfnAssistException {
		SecurityGroup group = TestSubnetDiagramBuilder.setupSecurityGroup();
		IpPermission ipPerms = TestSubnetDiagramBuilder.setupIpPerms();
		group.withIpPermissions(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_in", "80-100");
		securityDiagram.connectWithLabel("groupId_tcp_80-100_in", "groupId", "(ipRanges)\n[tcp]");
		
		replayAll();
		builder.addSecGroupInboundPerms("groupId", ipPerms);
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsOutboundWithSubnet() throws CfnAssistException {
		IpPermission perms = new IpPermission().withFromPort(80);

		SubnetDiagramBuilder subnetDiaBuilder = setupSubnetDiagramBuidler();
		subnetDiaBuilder.addSecGroupOutboundPerms("groupId", perms);
		
		replayAll();
		builder.addSecGroupOutboundPerms("groupId", perms, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldDisplaySecurityGroupDetailsOutbound() throws CfnAssistException {
		SecurityGroup group = TestSubnetDiagramBuilder.setupSecurityGroup();
		IpPermission ipPerms = TestSubnetDiagramBuilder.setupIpPerms();
		group.withIpPermissionsEgress(ipPerms);
		
		securityDiagram.addPortRange("groupId_tcp_80-100_out", "80-100");
		securityDiagram.connectWithLabel("groupId", "groupId_tcp_80-100_out", "(ipRanges)\n[tcp]");
		
		replayAll();
		builder.addSecGroupOutboundPerms("groupId", ipPerms);
		verifyAll();
	}
	
	@Test
	public void shouldSecGroup() throws CfnAssistException {
		SecurityGroup secGroup = new SecurityGroup().withGroupId("groupId").withGroupName("groupName");
		
		securityDiagram.addSecurityGroup("groupId", "groupName [groupId]");
		
		replayAll();
		builder.addSecurityGroup(secGroup);
		verifyAll();
	}

	private NetworkAclEntry createAclEntry(PortRange thePortRange, String cidrBlock, Boolean outbound, Integer ruleNumber, 
			RuleAction ruleAction) {
		NetworkAclEntry outboundEntry = new NetworkAclEntry().
				withCidrBlock(cidrBlock).
				withEgress(outbound).
				withPortRange(thePortRange).
				withProtocol("6").
				withRuleAction(ruleAction).
				withRuleNumber(ruleNumber);
		return outboundEntry;
	}
	
	private SubnetDiagramBuilder setupSubnetDiagramBuidler() {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		return subnetDiagramBuilder;
	}
}

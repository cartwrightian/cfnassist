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

		replayAll();
		builder.addDBInstance(rds);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateDBWithSubent() throws CfnAssistException {
		DBInstance rds = new DBInstance().withDBName("dbName").withDBInstanceIdentifier("instanceID");

		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		networkDiagram.associateWithSubDiagram("instanceID", "subnetId", subnetDiagramBuilder);
	
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
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		networkDiagram.associateWithSubDiagram("dnsName", "subnetId", subnetDiagramBuilder);
		
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
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		networkDiagram.associateWithSubDiagram("cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddLocalRouteNoCIDR() throws CfnAssistException {
		Route route = new Route().withGatewayId("local");
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		networkDiagram.associateWithSubDiagram("no cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddNonLocalRoute() throws CfnAssistException {
		Route route = new Route().withGatewayId("gatewayId").withDestinationCidrBlock("cidr");
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		networkDiagram.addConnectionFromSubDiagram("gatewayId", "subnetId", subnetDiagramBuilder, "cidr");
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithSubnet() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").
				withTags(VpcTestBuilder.CreateNameTag("rtName"));
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		subnetDiagramBuilder.addRouteTable(routeTable);
	
		replayAll();
		builder.add("subnetId", subnetDiagramBuilder);
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
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		securityDiagram.associateWithSubDiagram("networkAclId", "subnetId", subnetDiagramBuilder);

		replayAll();
		builder.associateAclWithSubnet(acl, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "cidrBlock", true, 42, RuleAction.Allow);
		
		securityDiagram.addCidr("out_cidrBlock_aclId","cidrBlock");
		securityDiagram.addConnectionFromSubDiagram("out_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addOutboundRoute("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllCidrAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0", true, 42, RuleAction.Allow);
		
		securityDiagram.addCidr("out_0.0.0.0/0_aclId","any");
		securityDiagram.addConnectionFromSubDiagram("out_0.0.0.0/0_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addOutboundRoute("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllCidrBlocked() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0", true, 42, RuleAction.Deny);
		
		securityDiagram.addCidr("out_0.0.0.0/0_aclId","any");
		securityDiagram.addBlockedConnectionFromSubDiagram("out_0.0.0.0/0_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addOutboundRoute("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryNoPortRangeAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry entry = createAclEntry(null, "cidrBlock", true, 42, RuleAction.Allow);
		
		securityDiagram.addCidr("out_cidrBlock_aclId","cidrBlock");
		securityDiagram.addConnectionFromSubDiagram("out_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[all]\n(rule:42)");
		
		replayAll();
		builder.addOutboundRoute("aclId", entry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddInboundAclEntryAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 42, RuleAction.Allow);
		
		securityDiagram.addCidr("in_cidrBlock_aclId", "cidrBlock");
		securityDiagram.addConnectionToSubDiagram("in_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addInboundRoute("aclId", entry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddInboundAclEntryBlocked() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry entry = createAclEntry(portRange, "cidrBlock", false, 42, RuleAction.Deny);
		
		securityDiagram.addCidr("in_cidrBlock_aclId", "cidrBlock");
		securityDiagram.addBlockedConnectionToSubDiagram("in_cidrBlock_aclId", "subnetId", subnetDiagramBuilder, 
				"tcp:[1023-1128]\n(rule:42)");
		
		replayAll();
		builder.addInboundRoute("aclId", entry, "subnetId");
		verifyAll();
	}
		
	@Test
	public void shouldAddSecurityGroup() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		SecurityGroup group = new SecurityGroup().withGroupId("groupId");
		
		subnetDiagramBuilder.addSecurityGroup(group);
		
		replayAll();
		builder.addSecurityGroup(group, "subnetId", "instanceId");
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
	

	
}

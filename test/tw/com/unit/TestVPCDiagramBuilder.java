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
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

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

		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
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
		
		replayAll();
		builder.addELB(elb);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateELBWithSubnet() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
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
		
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
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
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").withTags(createNameTag("rtName"));
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		subnetDiagramBuilder.addRouteTable(routeTable);
	
		replayAll();
		builder.add("subnetId", subnetDiagramBuilder);
		builder.addRouteTable(routeTable, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithoutSubnet() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").withTags(createNameTag("rtName"));
		
		networkDiagram.addRouteTable("rtId", "rtName [rtId]");
	
		replayAll();
		builder.addRouteTable(routeTable, null);
		verifyAll();
	}
	
	@Test
	public void shouldAddACLs() throws CfnAssistException {
		NetworkAcl acl = new NetworkAcl().withNetworkAclId("networkAclId").withTags(createNameTag("ACL"));
		securityDiagram.addACL("networkAclId","ACL [networkAclId]");

		replayAll();
		builder.addAcl(acl);
		verifyAll();
	}
	
	@Test
	public void shouldAddAssociateACLWithSubnet() throws CfnAssistException {
		NetworkAcl acl = new NetworkAcl().withNetworkAclId("networkAclId").withTags(createNameTag("ACL"));
		
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		securityDiagram.associateWithSubDiagram("networkAclId", "subnetId", subnetDiagramBuilder);

		replayAll();
		builder.associateAclWithSubnet(acl, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntry() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "cidrBlock");
		
		securityDiagram.addCidr("cidrBlock");
		securityDiagram.addPortRange("aclId_42_cidrBlock", 1023, 1128);
		securityDiagram.addConnectionFromSubDiagram("aclId_42_cidrBlock", "subnetId", subnetDiagramBuilder, "cidrBlock");
		securityDiagram.addConnectionBetween("aclId_42_cidrBlock", "cidrBlock");
		
		replayAll();
		builder.addOutboundRoute("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryAllCidrAllowed() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry outboundEntry = createAclEntry(portRange, "0.0.0.0/0");
		
		securityDiagram.addPortRange("aclId_42_any", 1023, 1128);
		securityDiagram.addConnectionFromSubDiagram("aclId_42_any", "subnetId", subnetDiagramBuilder, "any");
		
		replayAll();
		builder.addOutboundRoute("aclId", outboundEntry, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddOutboundAclEntryNoPortRange() throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		NetworkAclEntry outboundEntry = createAclEntry(null, "cidrBlock");
		
		securityDiagram.addCidr("cidrBlock");
		securityDiagram.addPortRange("aclId_42_cidrBlock", 0, 0);
		securityDiagram.addConnectionFromSubDiagram("aclId_42_cidrBlock", "subnetId", subnetDiagramBuilder, "cidrBlock");
		securityDiagram.addConnectionBetween("aclId_42_cidrBlock", "cidrBlock");
		
		replayAll();
		builder.addOutboundRoute("aclId", outboundEntry, "subnetId");
		verifyAll();
	}

	private NetworkAclEntry createAclEntry(PortRange thePortRange, String cidrBlock) {
		NetworkAclEntry outboundEntry = new NetworkAclEntry().
				withCidrBlock(cidrBlock).
				withEgress(true).
				withPortRange(thePortRange).
				withProtocol("proto").
				withRuleAction(RuleAction.Allow).
				withRuleNumber(42);
		return outboundEntry;
	}
	
	private Tag createNameTag(String routeTableName) {
		return new Tag().withKey("Name").withValue(routeTableName);
	}
	
}

package tw.com.unit;

import static org.junit.Assert.*;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;
import tw.com.pictures.Diagram;
import tw.com.pictures.SubnetDiagramBuilder;
import tw.com.pictures.VPCDiagramBuilder;

@RunWith(EasyMockRunner.class)
public class TestVPCDiagramBuilder extends EasyMockSupport {
	VPCDiagramBuilder builder;
	private Vpc vpc;
	private Diagram diagram;
	private String vpcId = "theVpcId";
	private ChildDiagram childDiagram;
	
	@Before
	public void beforeEachTestRuns() {
		vpc = new Vpc().withVpcId(vpcId);
		diagram = createStrictMock(Diagram.class);
		builder = new VPCDiagramBuilder(vpc, diagram);
		childDiagram = createStrictMock(ChildDiagram.class);
	}
	
	@Test
	public void shouldCreateSubDiagramForClusters() throws CfnAssistException {
		
		EasyMock.expect(diagram.createDiagramCluster("theId", "theLabel")).andReturn(childDiagram);
		
		replayAll();
		ChildDiagram result = builder.createDiagramCluster("theId", "theLabel");
		verifyAll();
		assertSame(childDiagram,result);
	}
	
	@Test
	public void shouldAddEIP() throws CfnAssistException {
		Address eip = new Address().withPublicIp("publicIP").withAllocationId("allocId");
		diagram.addEIP("publicIP", "publicIP [allocId]");

		replayAll();
		builder.addEIP(eip);
		verifyAll();
	}
	
	@Test
	public void shouldAddDB() throws CfnAssistException {
		DBInstance rds = new DBInstance().withDBName("dbName").withDBInstanceIdentifier("instanceID");
		diagram.addDBInstance("instanceID", "dbName [instanceID]");

		replayAll();
		builder.addDBInstance(rds);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateDBWithSubent() throws CfnAssistException {
		DBInstance rds = new DBInstance().withDBName("dbName").withDBInstanceIdentifier("instanceID");

		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		diagram.associateWithSubDiagram("instanceID", "subnetId", subnetDiagramBuilder);
	
		replayAll();
		builder.associateDBWithSubnet(rds, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddELB() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");
		diagram.addELB("dnsName", "lbName");
		
		replayAll();
		builder.addELB(elb);
		verifyAll();
	}
	
	@Test
	public void shouldAssociateELBWithSubnet() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);
		
		diagram.associateWithSubDiagram("dnsName", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.associateELBToSubnet(elb, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAssociateELBWithInstance() throws CfnAssistException {
		LoadBalancerDescription elb = new LoadBalancerDescription().withDNSName("dnsName").withLoadBalancerName("lbName");	
		diagram.addConnectionBetween("dnsName", "instanceId");
		
		replayAll();
		builder.associateELBToInstance(elb, "instanceId");
		verifyAll();
	}
	
	@Test
	public void shouldAddLocalRoute() throws CfnAssistException {
		Route route = new Route().withGatewayId("local").withDestinationCidrBlock("cidr");
		
		SubnetDiagramBuilder subnetDiagramBuilder = EasyMock.createMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		diagram.associateWithSubDiagram("cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddLocalRouteNoCIDR() throws CfnAssistException {
		Route route = new Route().withGatewayId("local");
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		diagram.associateWithSubDiagram("no cidr", "subnetId", subnetDiagramBuilder);
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddNonLocalRoute() throws CfnAssistException {
		Route route = new Route().withGatewayId("gatewayId").withDestinationCidrBlock("cidr");
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		builder.add("subnetId", subnetDiagramBuilder);

		diagram.addConnectionFromSubDiagram("gatewayId", "subnetId", subnetDiagramBuilder, "cidr");
		
		replayAll();
		builder.addRoute("subnetId", route);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithSubnet() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").withTags(new Tag().withKey("Name").withValue("rtName"));
		
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		subnetDiagramBuilder.addRouteTable(routeTable);
	
		replayAll();
		builder.add("subnetId", subnetDiagramBuilder);
		builder.addRouteTable(routeTable, "subnetId");
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTableWithoutSubnet() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().withRouteTableId("rtId").withTags(new Tag().withKey("Name").withValue("rtName"));
		
		diagram.addRouteTable("rtId", "rtName [rtId]");
	
		replayAll();
		builder.addRouteTable(routeTable, null);
		verifyAll();
	}
	
}

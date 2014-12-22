package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RouteTableAssociation;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramBuilder;
import tw.com.pictures.DiagramFactory;
import tw.com.pictures.SubnetDiagramBuilder;
import tw.com.pictures.VPCDiagramBuilder;
import tw.com.pictures.VPCVisitor;

@RunWith(EasyMockRunner.class)
public class TestVPCVisitor extends EasyMockSupport {
	
	private DiagramBuilder diagramBuilder;
	private DiagramFactory diagramFactory;
	private AmazonVPCFacade awsFacade;
	private String vpcId = "theVpcId";
	private VpcTestBuilder vpcBuilder;

	@Before
	public void beforeEveryTestRuns() {
		awsFacade = createStrictMock(AmazonVPCFacade.class);
		diagramBuilder = createStrictMock(DiagramBuilder.class);
		diagramFactory = createStrictMock(DiagramFactory.class);
		
		vpcBuilder = new VpcTestBuilder(vpcId);	
	}
	
	@Test
	public void shouldWalkVPCAndAddItems() throws CfnAssistException {
		
		Subnet subnet = new Subnet().withSubnetId("subnetIdA").withCidrBlock("cidrBlockA");
		String subnetId = subnet.getSubnetId();
		Instance instance = new Instance().withInstanceId("instanceId");
		String instanceId = instance.getInstanceId();

		vpcBuilder.add(subnet);
		vpcBuilder.add(instance);
		RouteTableAssociation association = new RouteTableAssociation().withRouteTableAssociationId("assocId").withSubnetId(subnetId);
		RouteTable routeTable = new RouteTable().withRouteTableId("routeTableId").withAssociations(association);
		vpcBuilder.add(routeTable);
		Address eip = new Address().withAllocationId("eipAllocId").withInstanceId(instanceId).withPublicIp("publicIP");	
		vpcBuilder.add(eip);
		LoadBalancerDescription elb = new LoadBalancerDescription();
		vpcBuilder.addAndAssociate(elb);
		DBInstance dbInstance = new DBInstance();
		vpcBuilder.addAndAssociate(dbInstance);
		
		VPCDiagramBuilder vpcDiagram = createStrictMock(VPCDiagramBuilder.class);
		SubnetDiagramBuilder subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);

		Vpc vpc = vpcBuilder.setFacadeExpectations(awsFacade, subnetId);
		
		EasyMock.expect(diagramFactory.createVPCDiagramBuilder(vpc)).andReturn(vpcDiagram);
		EasyMock.expect(diagramFactory.createSubnetDiagramBuilder(vpcDiagram, subnet)).andReturn(subnetDiagramBuilder);
		subnetDiagramBuilder.add(instance);
		vpcDiagram.add(subnetId, subnetDiagramBuilder);
		vpcDiagram.addRouteTable(routeTable, subnetId);
		vpcDiagram.addEIP(eip);
		vpcDiagram.linkEIPToInstance(eip.getPublicIp(), instanceId);
		vpcDiagram.addELB(elb);
		vpcDiagram.associateELBToInstance(elb, instanceId);
		vpcDiagram.associateELBToSubnet(elb, subnetId);
		vpcDiagram.addDBInstance(dbInstance);
		vpcDiagram.associateRDSToSubnet(dbInstance, subnetId);
		diagramBuilder.add(vpcDiagram);
		
		replayAll();
		VPCVisitor visitor = new VPCVisitor(diagramBuilder, awsFacade, diagramFactory);
		visitor.visit(vpc);
		verifyAll();
	}

}

package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclAssociation;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.PortRange;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RouteTableAssociation;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

import tw.com.VpcTestBuilder;
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
	private VPCDiagramBuilder vpcDiagramBuilder;
	private SubnetDiagramBuilder subnetDiagramBuilder;

	@Before
	public void beforeEveryTestRuns() {
		awsFacade = createStrictMock(AmazonVPCFacade.class);
		
		diagramFactory = createStrictMock(DiagramFactory.class);
		diagramBuilder = createStrictMock(DiagramBuilder.class);
		vpcDiagramBuilder = createStrictMock(VPCDiagramBuilder.class);
		subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);

		vpcBuilder = new VpcTestBuilder(vpcId);	
	}
	
	@Test
	public void shouldWalkVPCAndAddItemsForDiagram() throws CfnAssistException {
		
		Subnet subnet = new Subnet().withSubnetId("subnetIdA").withCidrBlock("cidrBlockA");
		String subnetId = subnet.getSubnetId();
		Instance instance = new Instance().withInstanceId("instanceId");
		String instanceId = instance.getInstanceId();
		RouteTableAssociation association = new RouteTableAssociation().withRouteTableAssociationId("assocId").withSubnetId(subnetId);
		RouteTable routeTable = new RouteTable().withRouteTableId("routeTableId").withAssociations(association);
		Address eip = new Address().withAllocationId("eipAllocId").withInstanceId(instanceId).withPublicIp("publicIP");	
		LoadBalancerDescription elb = new LoadBalancerDescription();
		DBInstance dbInstance = new DBInstance();
		NetworkAclAssociation aclAssoc = new NetworkAclAssociation().withSubnetId(subnetId);
		PortRange outboundPortRange = new PortRange().withFrom(1024).withTo(2048);
		NetworkAclEntry outboundEntry = new NetworkAclEntry().
				withEgress(true).
				withCidrBlock("cidrBlockOut").
				withPortRange(outboundPortRange).
				withRuleAction("allow").
				withProtocol("tcpip");
		NetworkAcl acl = new NetworkAcl().withAssociations(aclAssoc).withEntries(outboundEntry).withNetworkAclId("aclId");
		
		vpcBuilder.add(subnet);
		vpcBuilder.add(instance);	
		vpcBuilder.add(routeTable);
		vpcBuilder.add(eip);
		vpcBuilder.addAndAssociate(elb);
		vpcBuilder.addAndAssociate(dbInstance);
		vpcBuilder.add(acl);

		Vpc vpc = vpcBuilder.setFacadeExpectations(awsFacade, subnetId);
		
		EasyMock.expect(diagramFactory.createVPCDiagramBuilder(vpc)).andReturn(vpcDiagramBuilder);
		EasyMock.expect(diagramFactory.createSubnetDiagramBuilder(vpcDiagramBuilder, subnet)).andReturn(subnetDiagramBuilder);
		subnetDiagramBuilder.add(instance);
		vpcDiagramBuilder.add(subnetId, subnetDiagramBuilder);
		vpcDiagramBuilder.addRouteTable(routeTable, subnetId);
		vpcDiagramBuilder.addEIP(eip);
		vpcDiagramBuilder.linkEIPToInstance(eip.getPublicIp(), instanceId);
		vpcDiagramBuilder.addELB(elb);
		vpcDiagramBuilder.associateELBToInstance(elb, instanceId);
		vpcDiagramBuilder.associateELBToSubnet(elb, subnetId);
		vpcDiagramBuilder.addDBInstance(dbInstance);
		vpcDiagramBuilder.associateDBWithSubnet(dbInstance, subnetId);
		vpcDiagramBuilder.addAcl(acl);
		vpcDiagramBuilder.associateAclWithSubnet(acl, subnetId);
		vpcDiagramBuilder.addOutboundRoute("aclId",outboundEntry, subnetId);
		diagramBuilder.add(vpcDiagramBuilder);
		
		replayAll();
		VPCVisitor visitor = new VPCVisitor(diagramBuilder, awsFacade, diagramFactory);
		visitor.visit(vpc);
		verifyAll();
	}
	
}

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
import com.amazonaws.services.ec2.model.SecurityGroup;
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
	
	private Subnet subnet;
	private Instance instance;
	private RouteTable routeTable;
	private RouteTableAssociation association;
	private Address eip;
	private LoadBalancerDescription elb;
	private DBInstance dbInstance;
	private NetworkAclAssociation aclAssoc;
	private PortRange portRange;
	private NetworkAclEntry outboundEntry;
	private NetworkAclEntry inboundEntry;
	private NetworkAcl acl;
	private SecurityGroup securityGroup;
	private String subnetId;
	private String instanceId;

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
		
		createVPC();
		
		vpcBuilder.add(subnet);
		vpcBuilder.add(instance);	
		vpcBuilder.add(routeTable);
		vpcBuilder.add(eip);
		vpcBuilder.addAndAssociate(elb);
		vpcBuilder.addAndAssociate(dbInstance);
		vpcBuilder.add(acl);
		vpcBuilder.add(securityGroup);

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
		vpcDiagramBuilder.addInboundRoute("aclId", inboundEntry, subnetId);
		diagramBuilder.add(vpcDiagramBuilder);
		
		replayAll();
		VPCVisitor visitor = new VPCVisitor(diagramBuilder, awsFacade, diagramFactory);
		visitor.visit(vpc);
		verifyAll();
	}

	private void createVPC() {
		subnet = new Subnet().
				withSubnetId("subnetIdA").
				withCidrBlock("cidrBlockA");
		subnetId = subnet.getSubnetId();
		instance = new Instance().
				withInstanceId("instanceId");
		instanceId = instance.getInstanceId();
		association = new RouteTableAssociation().
				withRouteTableAssociationId("assocId").
				withSubnetId(subnetId);
		routeTable = new RouteTable().
				withRouteTableId("routeTableId").
				withAssociations(association);
		eip = new Address().
				withAllocationId("eipAllocId").
				withInstanceId(instanceId).
				withPublicIp("publicIP");	
		elb = new LoadBalancerDescription();
		dbInstance = new DBInstance();
		aclAssoc = new NetworkAclAssociation().
				withSubnetId(subnetId);
		portRange = new PortRange().
				withFrom(1024).
				withTo(2048);
		outboundEntry = new NetworkAclEntry().
				withEgress(true).
				withCidrBlock("cidrBlockOut").
				withPortRange(portRange).
				withRuleAction("allow").
				withProtocol("tcpip");
		inboundEntry = new NetworkAclEntry().
				withEgress(false).
				withCidrBlock("cidrBlockOut").
				withPortRange(portRange).
				withRuleAction("allow").
				withProtocol("tcpip");
		acl = new NetworkAcl().withAssociations(aclAssoc).
				withEntries(outboundEntry, inboundEntry).
				withNetworkAclId("aclId");
		securityGroup = new SecurityGroup().withGroupId("groupId").withGroupName("groupName");
	}
	
}

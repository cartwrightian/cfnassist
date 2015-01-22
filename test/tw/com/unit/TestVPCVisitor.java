package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.RouteTable;
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
	private SubnetDiagramBuilder dbSubnetDiagramBuilder;

	@Before
	public void beforeEveryTestRuns() {
		awsFacade = createStrictMock(AmazonVPCFacade.class);
		
		diagramFactory = createStrictMock(DiagramFactory.class);
		diagramBuilder = createStrictMock(DiagramBuilder.class);
		vpcDiagramBuilder = createStrictMock(VPCDiagramBuilder.class);
		subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		dbSubnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);

		vpcBuilder = new VpcTestBuilder(vpcId);	
	}
	
	@Test
	public void shouldWalkVPCAndAddItemsForDiagram() throws CfnAssistException {	

		Vpc vpc = vpcBuilder.setFacadeVisitExpections(awsFacade);
		
		String instanceSubnetId = vpcBuilder.getSubnetId();
		Subnet instanceSubnet = vpcBuilder.getSubnet();
		String dbSubnetId = vpcBuilder.getDbSubnetId();
		Subnet dbSubnet = vpcBuilder.getDbSubnet();

		Address eip = vpcBuilder.getEip();
		LoadBalancerDescription elb = vpcBuilder.getElb();
		DBInstance dbInstance = vpcBuilder.getDbInstance();
		Instance instance = vpcBuilder.getInstance();
		String instanceId = instance.getInstanceId();
		RouteTable routeTable = vpcBuilder.getRouteTable();
		NetworkAcl acl = vpcBuilder.getAcl();
		NetworkAclEntry outboundEntry = vpcBuilder.getOutboundEntry();
		NetworkAclEntry inboundEntry = vpcBuilder.getInboundEntry();
		SecurityGroup instanceSecurityGroup = vpcBuilder.getInstanceSecurityGroup();
		IpPermission instanceIpPermsInbound = vpcBuilder.getInstanceIpPermsInbound();
		IpPermission instanceIpPermsOutbound = vpcBuilder.getInstanceIpPermsOutbound();
		SecurityGroup dbSecurityGroup = vpcBuilder.getDBSecurityGroup();
		IpPermission dbIpPermsInbound = vpcBuilder.getDbIpPermsInbound();
		IpPermission dbIpPermsOutbound = vpcBuilder.getDbIpPermsOutbound();
		SecurityGroup elbSecurityGroup = vpcBuilder.getElbSecurityGroup();
		
		EasyMock.expect(diagramFactory.createVPCDiagramBuilder(vpc)).andReturn(vpcDiagramBuilder);
		EasyMock.expect(diagramFactory.createSubnetDiagramBuilder(vpcDiagramBuilder, instanceSubnet)).andReturn(subnetDiagramBuilder);
		EasyMock.expect(diagramFactory.createSubnetDiagramBuilder(vpcDiagramBuilder, dbSubnet)).andReturn(dbSubnetDiagramBuilder);

		subnetDiagramBuilder.add(instance);
		vpcDiagramBuilder.add(instanceSubnetId, subnetDiagramBuilder);
		vpcDiagramBuilder.add(dbSubnetId, dbSubnetDiagramBuilder);
		// route table & routes
		vpcDiagramBuilder.addAsssociatedRouteTable(routeTable, instanceSubnetId);
		vpcDiagramBuilder.addRoute(routeTable.getRouteTableId(), instanceSubnetId, vpcBuilder.getRouteA());
		vpcDiagramBuilder.addRoute(routeTable.getRouteTableId(), instanceSubnetId, vpcBuilder.getRouteB());
		vpcDiagramBuilder.addRoute(routeTable.getRouteTableId(), instanceSubnetId, vpcBuilder.getRouteC());
		vpcDiagramBuilder.addAsssociatedRouteTable(routeTable, dbSubnetId);
		vpcDiagramBuilder.addRoute(routeTable.getRouteTableId(), dbSubnetId, vpcBuilder.getRouteA());
		vpcDiagramBuilder.addRoute(routeTable.getRouteTableId(), dbSubnetId, vpcBuilder.getRouteB());
		vpcDiagramBuilder.addRoute(routeTable.getRouteTableId(), dbSubnetId, vpcBuilder.getRouteC());
		// eip
		vpcDiagramBuilder.addEIP(eip);
		vpcDiagramBuilder.linkEIPToInstance(eip.getPublicIp(), instanceId);
		// elb
		vpcDiagramBuilder.addELB(elb);
		vpcDiagramBuilder.associateELBToInstance(elb, instanceId);
		vpcDiagramBuilder.associateELBToSubnet(elb, instanceSubnetId);
		vpcDiagramBuilder.associateELBToSubnet(elb, dbSubnetId);
		vpcDiagramBuilder.addSecurityGroup(elbSecurityGroup);
		vpcDiagramBuilder.associateInstanceWithSecGroup(elb.getDNSName(), elbSecurityGroup);
		vpcDiagramBuilder.addSecGroupInboundPerms("secElbGroupId", vpcBuilder.getElbIpPermsInbound());
		vpcDiagramBuilder.addSecGroupOutboundPerms("secElbGroupId", vpcBuilder.getElbIpPermsOutbound());
		// db
		vpcDiagramBuilder.addDBInstance(dbInstance);
		vpcDiagramBuilder.associateDBWithSubnet(dbInstance, dbSubnetId);
		vpcDiagramBuilder.addSecurityGroup(dbSecurityGroup);
		vpcDiagramBuilder.associateInstanceWithSecGroup(dbInstance.getDBInstanceIdentifier(), dbSecurityGroup);
		vpcDiagramBuilder.addSecGroupInboundPerms("secDbGroupId",dbIpPermsInbound);
		vpcDiagramBuilder.addSecGroupOutboundPerms("secDbGroupId",dbIpPermsOutbound);
		// acl
		vpcDiagramBuilder.addAcl(acl);
		vpcDiagramBuilder.associateAclWithSubnet(acl, instanceSubnetId);
		vpcDiagramBuilder.addACLOutbound("aclId",outboundEntry, instanceSubnetId);
		vpcDiagramBuilder.addACLInbound("aclId", inboundEntry, instanceSubnetId);
		// sec group
		vpcDiagramBuilder.addSecurityGroup(instanceSecurityGroup, instanceSubnetId);
		vpcDiagramBuilder.associateInstanceWithSecGroup(instanceId, instanceSecurityGroup);
		vpcDiagramBuilder.addSecGroupInboundPerms("secGroupId",instanceIpPermsInbound, instanceSubnetId);
		vpcDiagramBuilder.addSecGroupOutboundPerms("secGroupId",instanceIpPermsOutbound, instanceSubnetId);
		diagramBuilder.add(vpcDiagramBuilder);
		
		replayAll();
		VPCVisitor visitor = new VPCVisitor(diagramBuilder, awsFacade, diagramFactory);
		visitor.visit(vpc);
		verifyAll();
	}

	
	
}

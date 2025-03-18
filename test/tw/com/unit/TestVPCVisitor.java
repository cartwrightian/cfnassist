package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.rds.model.DBInstance;
import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramBuilder;
import tw.com.pictures.DiagramFactory;
import tw.com.pictures.SubnetDiagramBuilder;
import tw.com.pictures.VPCDiagramBuilder;
import tw.com.pictures.VPCVisitor;

public class TestVPCVisitor extends EasyMockSupport {
	
	private DiagramBuilder diagramBuilder;
	private DiagramFactory diagramFactory;
	private AmazonVPCFacade awsFacade;
	private VpcTestBuilder vpcBuilder;
	private VPCDiagramBuilder vpcDiagramBuilder;
	private SubnetDiagramBuilder subnetDiagramBuilder;
	private SubnetDiagramBuilder dbSubnetDiagramBuilder;

	@BeforeEach
	public void beforeEveryTestRuns() {
		awsFacade = createStrictMock(AmazonVPCFacade.class);
		
		diagramFactory = createStrictMock(DiagramFactory.class);
		diagramBuilder = createStrictMock(DiagramBuilder.class);
		vpcDiagramBuilder = createStrictMock(VPCDiagramBuilder.class);
		subnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);
		dbSubnetDiagramBuilder = createStrictMock(SubnetDiagramBuilder.class);

		String vpcId = "theVpcId";
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
		String instanceId = instance.instanceId();
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
		vpcDiagramBuilder.addRoute(routeTable.routeTableId(), instanceSubnetId, vpcBuilder.getRouteA());
		vpcDiagramBuilder.addRoute(routeTable.routeTableId(), instanceSubnetId, vpcBuilder.getRouteB());
		vpcDiagramBuilder.addRoute(routeTable.routeTableId(), instanceSubnetId, vpcBuilder.getRouteC());
		vpcDiagramBuilder.addAsssociatedRouteTable(routeTable, dbSubnetId);
		vpcDiagramBuilder.addRoute(routeTable.routeTableId(), dbSubnetId, vpcBuilder.getRouteA());
		vpcDiagramBuilder.addRoute(routeTable.routeTableId(), dbSubnetId, vpcBuilder.getRouteB());
		vpcDiagramBuilder.addRoute(routeTable.routeTableId(), dbSubnetId, vpcBuilder.getRouteC());
		// eip
		vpcDiagramBuilder.addEIP(eip);
		vpcDiagramBuilder.linkEIPToInstance(eip.publicIp(), instanceId);
		// elb
		vpcDiagramBuilder.addELB(elb);
		vpcDiagramBuilder.associateELBToInstance(elb, instanceId);
		vpcDiagramBuilder.associateELBToSubnet(elb, instanceSubnetId);
		vpcDiagramBuilder.associateELBToSubnet(elb, dbSubnetId);
		vpcDiagramBuilder.addSecurityGroup(elbSecurityGroup);
		vpcDiagramBuilder.associateInstanceWithSecGroup(elb.dnsName(), elbSecurityGroup);
		vpcDiagramBuilder.addSecGroupInboundPerms("secElbGroupId", vpcBuilder.getElbIpPermsInbound());
		vpcDiagramBuilder.addSecGroupOutboundPerms("secElbGroupId", vpcBuilder.getElbIpPermsOutbound());
		// db
		vpcDiagramBuilder.addDBInstance(dbInstance);
		vpcDiagramBuilder.associateDBWithSubnet(dbInstance, dbSubnetId);
		vpcDiagramBuilder.addSecurityGroup(dbSecurityGroup);
		vpcDiagramBuilder.associateInstanceWithSecGroup(dbInstance.dbInstanceIdentifier(), dbSecurityGroup);
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

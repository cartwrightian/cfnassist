package tw.com.pictures;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.ec2.model.Address;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.NetworkAcl;
import software.amazon.awssdk.services.ec2.model.NetworkAclAssociation;
import software.amazon.awssdk.services.ec2.model.NetworkAclEntry;
import software.amazon.awssdk.services.ec2.model.Route;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.RouteTableAssociation;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Vpc;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSecurityGroupMembership;
import com.amazonaws.services.rds.model.DBSubnetGroup;
import com.amazonaws.services.rds.model.VpcSecurityGroupMembership;

import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import tw.com.exceptions.CfnAssistException;

public class VPCVisitor {
	private static final Logger logger = LoggerFactory.getLogger(VPCVisitor.class);

	private DiagramBuilder diagramBuilder;
	private AmazonVPCFacade facade;
	private DiagramFactory factory;

	public VPCVisitor(DiagramBuilder diagramBuilder, AmazonVPCFacade facade, DiagramFactory factory) {
		this.diagramBuilder = diagramBuilder;
		this.facade = facade;
		this.factory = factory;
	}

	public void visit(Vpc vpc) throws CfnAssistException {	
		VPCDiagramBuilder vpcDiagram = factory.createVPCDiagramBuilder(vpc);
		String vpcId = vpc.vpcId();
		///
		for (Subnet subnet : facade.getSubnetFors(vpcId)) {
			visitSubnet(vpcDiagram, subnet);
		}
		///
		for(RouteTable table : facade.getRouteTablesFor(vpcId)) {
			visitRouteTable(vpcDiagram, table);
		}
		//
		for(Address eip : facade.getEIPFor(vpcId)) {
			visitEIP(vpcDiagram, eip);
		}
		// 
		for(LoadBalancerDescription elb : facade.getLBsFor(vpcId)) {
			visitELB(vpcDiagram, elb);
		}
		//
		for(DBInstance rds : facade.getRDSFor(vpcId)) {
			visitRDS(vpcDiagram, rds);
		}
		//
		for(NetworkAcl acl : facade.getACLs(vpcId)) {
			visitNetworkAcl(vpcDiagram, acl);
		}
		//
		for (Subnet subnet : facade.getSubnetFors(vpcId)) {
			visitSubnetForInstancesAndSecGroups(vpcDiagram, subnet);
		}
		
		diagramBuilder.add(vpcDiagram);
	}

	private void visitRDS(VPCDiagramBuilder vpcDiagram, DBInstance rds) throws CfnAssistException {
		logger.debug("visit rds " + rds.getDBInstanceIdentifier());
		vpcDiagram.addDBInstance(rds);
		DBSubnetGroup dbSubnetGroup = rds.getDBSubnetGroup();

		if (dbSubnetGroup!=null) {
			for(com.amazonaws.services.rds.model.Subnet subnet : dbSubnetGroup.getSubnets()) {
				String subnetId = subnet.getSubnetIdentifier();
				logger.debug("visit rds subnet " + subnetId);
				vpcDiagram.associateDBWithSubnet(rds, subnetId);
			}
		}	
		addDBSecurityGroupsToDiagram(vpcDiagram, rds);
	}

	private void addDBSecurityGroupsToDiagram(VPCDiagramBuilder vpcDiagram, DBInstance rds) throws CfnAssistException {
		String dbInstanceIdentifier = rds.getDBInstanceIdentifier();
		for(DBSecurityGroupMembership secGroupMember : rds.getDBSecurityGroups()) {
			String groupName = secGroupMember.getDBSecurityGroupName();
			SecurityGroup dbSecGroup = facade.getSecurityGroupDetailsByName(groupName);
			logger.debug("visit rds secgroup " + dbSecGroup.groupId());
			addSecGroupToDiagram(vpcDiagram, dbInstanceIdentifier, dbSecGroup);
		}
		//
		for(VpcSecurityGroupMembership secGroupMember : rds.getVpcSecurityGroups()) {
			String groupId = secGroupMember.getVpcSecurityGroupId();
			SecurityGroup dbSecGroup = facade.getSecurityGroupDetailsById(groupId);
			logger.debug("visit rds vpc secgroup " + dbSecGroup.groupId());
			addSecGroupToDiagram(vpcDiagram, dbInstanceIdentifier, dbSecGroup);
		}
	}

	private void addSecGroupToDiagram(VPCDiagramBuilder vpcDiagram, String instanceId, SecurityGroup dbSecGroup) throws CfnAssistException {
		vpcDiagram.addSecurityGroup(dbSecGroup);
		vpcDiagram.associateInstanceWithSecGroup(instanceId, dbSecGroup);
		String groupId = dbSecGroup.groupId();
		for(IpPermission perm : dbSecGroup.ipPermissions()) {
			vpcDiagram.addSecGroupInboundPerms(groupId, perm);
		}
		for(IpPermission perm : dbSecGroup.ipPermissionsEgress()) {
			vpcDiagram.addSecGroupOutboundPerms(groupId, perm);
		}
	}

	private void visitELB(VPCDiagramBuilder vpcDiagramBuilder, LoadBalancerDescription elb) throws CfnAssistException {
		logger.debug("visit elb " + elb.loadBalancerName());

		vpcDiagramBuilder.addELB(elb);
		for(software.amazon.awssdk.services.elasticloadbalancing.model.Instance instance : elb.instances()) {
			vpcDiagramBuilder.associateELBToInstance(elb, instance.instanceId());
		}
		for(String subnetId : elb.subnets()) {
			vpcDiagramBuilder.associateELBToSubnet(elb, subnetId);
		}
		for(String groupId : elb.securityGroups()) {
			SecurityGroup group = facade.getSecurityGroupDetailsById(groupId);
			addSecGroupToDiagram(vpcDiagramBuilder, elb.dnsName(), group);
		}
	}
	
	private void visitSubnetForInstancesAndSecGroups(VPCDiagramBuilder vpcDiagramBuilder, Subnet subnet) throws CfnAssistException {
		String subnetId = subnet.subnetId();
		logger.debug("visit subnet (for sec groups) " + subnetId); 
		for(Instance instance : facade.getInstancesFor(subnetId)) {
			for(GroupIdentifier groupId : instance.securityGroups()) {
				logger.debug("visit securitygroup " + groupId.groupId() + " for instance " + instance.instanceId());

				SecurityGroup group = facade.getSecurityGroupDetailsById(groupId.groupId());
				vpcDiagramBuilder.addSecurityGroup(group, subnetId);
				vpcDiagramBuilder.associateInstanceWithSecGroup(instance.instanceId(), group);
				visitInboundSecGroupPerms(vpcDiagramBuilder, group, subnetId);
				visitOutboundSecGroupPerms(vpcDiagramBuilder, group, subnetId);
			}
		}		
	}
	
	private void visitOutboundSecGroupPerms(VPCDiagramBuilder vpcDiagramBuilder, SecurityGroup group, String subnetId) throws CfnAssistException {
		for(IpPermission perm : group.ipPermissionsEgress()) {
			vpcDiagramBuilder.addSecGroupOutboundPerms(group.groupId(), perm, subnetId);
		}	
	}

	private void visitInboundSecGroupPerms(VPCDiagramBuilder vpcDiagramBuilder, SecurityGroup group, String subnetId) throws CfnAssistException {
		for(IpPermission perm : group.ipPermissions()) {
			vpcDiagramBuilder.addSecGroupInboundPerms(group.groupId(), perm, subnetId);
		}	
	}

	private void visitNetworkAcl(VPCDiagramBuilder vpcDiagramBuilder, NetworkAcl acl) throws CfnAssistException {
		vpcDiagramBuilder.addAcl(acl);
		String networkAclId = acl.networkAclId();
		logger.debug("visit acl " + networkAclId);

		for(NetworkAclAssociation assoc : acl.associations()) {
			String subnetId = assoc.subnetId();
			vpcDiagramBuilder.associateAclWithSubnet(acl, subnetId);
			
			for(NetworkAclEntry entry : acl.entries()) {
				if (entry.egress()) {
					vpcDiagramBuilder.addACLOutbound(networkAclId, entry, subnetId);
				} else {
					vpcDiagramBuilder.addACLInbound(networkAclId, entry, subnetId);
				}
			}			
		}	
	}

	private void visitEIP(VPCDiagramBuilder vpcDiagram, Address eip) throws CfnAssistException {
		logger.debug("visit eip " + eip.allocationId());

		vpcDiagram.addEIP(eip);

		String instanceId = eip.instanceId();
		if (instanceId!=null) {
			vpcDiagram.linkEIPToInstance(eip.publicIp(), instanceId);
		}	
	}

	private void visitSubnet(VPCDiagramBuilder parent, Subnet subnet) throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagram = factory.createSubnetDiagramBuilder(parent, subnet);
		
		String subnetId = subnet.subnetId();
		logger.debug("visit subnet " + subnetId);

		List<Instance> instances = facade.getInstancesFor(subnetId);
		for(Instance instance : instances) {
			visit(subnetDiagram, instance);
		}
		parent.add(subnetId, subnetDiagram);	
	}
	
	private void visitRouteTable(VPCDiagramBuilder vpcDiagram, RouteTable routeTable) throws CfnAssistException {
		logger.debug("visit routetable " + routeTable.routeTableId());
		List<Route> routes = routeTable.routes();
		List<RouteTableAssociation> usersOfTable = routeTable.associations();
		for (RouteTableAssociation usedBy : usersOfTable) {
			String subnetId = usedBy.subnetId(); // can subnet ever be null in an association?
			
			if (subnetId!=null) {
				vpcDiagram.addAsssociatedRouteTable(routeTable, subnetId); // possible duplication if route table reused?
				for (Route route : routes) {
					vpcDiagram.addRoute(routeTable.routeTableId(), subnetId, route);
				}
			} 
		}
 	}

	private void visit(SubnetDiagramBuilder subnetDiagram, Instance instance) throws CfnAssistException {
		logger.debug("visit instance " + instance.instanceId());
		subnetDiagram.add(instance);
	}

}

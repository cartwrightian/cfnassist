package tw.com.pictures;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclAssociation;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RouteTableAssociation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSecurityGroupMembership;
import com.amazonaws.services.rds.model.DBSubnetGroup;
import com.amazonaws.services.rds.model.VpcSecurityGroupMembership;

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
		String vpcId = vpc.getVpcId();
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
			logger.debug("visit rds secgroup " + dbSecGroup.getGroupId());
			addSecGroupToDiagram(vpcDiagram, dbInstanceIdentifier, dbSecGroup);
		}
		//
		for(VpcSecurityGroupMembership secGroupMember : rds.getVpcSecurityGroups()) {
			String groupId = secGroupMember.getVpcSecurityGroupId();
			SecurityGroup dbSecGroup = facade.getSecurityGroupDetailsById(groupId);
			logger.debug("visit rds vpc secgroup " + dbSecGroup.getGroupId());
			addSecGroupToDiagram(vpcDiagram, dbInstanceIdentifier, dbSecGroup);
		}
	}

	private void addSecGroupToDiagram(VPCDiagramBuilder vpcDiagram, String instanceId, SecurityGroup dbSecGroup) throws CfnAssistException {
		vpcDiagram.addSecurityGroup(dbSecGroup);
		vpcDiagram.associateInstanceWithSecGroup(instanceId, dbSecGroup);
		String groupId = dbSecGroup.getGroupId();
		for(IpPermission perm : dbSecGroup.getIpPermissions()) {
			vpcDiagram.addSecGroupInboundPerms(groupId, perm);
		}
		for(IpPermission perm : dbSecGroup.getIpPermissionsEgress()) {
			vpcDiagram.addSecGroupOutboundPerms(groupId, perm);
		}
	}

	private void visitELB(VPCDiagramBuilder vpcDiagramBuilder, LoadBalancerDescription elb) throws CfnAssistException {
		logger.debug("visit elb " + elb.getLoadBalancerName()); 

		vpcDiagramBuilder.addELB(elb);
		for(com.amazonaws.services.elasticloadbalancing.model.Instance instance : elb.getInstances()) {
			vpcDiagramBuilder.associateELBToInstance(elb, instance.getInstanceId());
		}
		for(String subnetId : elb.getSubnets()) {
			vpcDiagramBuilder.associateELBToSubnet(elb, subnetId);
		}
		for(String groupId : elb.getSecurityGroups()) {
			SecurityGroup group = facade.getSecurityGroupDetailsById(groupId);
			addSecGroupToDiagram(vpcDiagramBuilder, elb.getDNSName(), group);
		}
	}
	
	private void visitSubnetForInstancesAndSecGroups(VPCDiagramBuilder vpcDiagramBuilder, Subnet subnet) throws CfnAssistException {
		String subnetId = subnet.getSubnetId();
		logger.debug("visit subnet (for sec groups) " + subnetId); 
		for(Instance instance : facade.getInstancesFor(subnetId)) {
			for(GroupIdentifier groupId : instance.getSecurityGroups()) {
				logger.debug("visit securitygroup " + groupId.getGroupId() + " for instance " + instance.getInstanceId());

				SecurityGroup group = facade.getSecurityGroupDetailsById(groupId.getGroupId());
				vpcDiagramBuilder.addSecurityGroup(group, subnetId);
				vpcDiagramBuilder.associateInstanceWithSecGroup(instance.getInstanceId(), group);
				visitInboundSecGroupPerms(vpcDiagramBuilder, group, subnetId);
				visitOutboundSecGroupPerms(vpcDiagramBuilder, group, subnetId);
			}
		}		
	}
	
	private void visitOutboundSecGroupPerms(VPCDiagramBuilder vpcDiagramBuilder, SecurityGroup group, String subnetId) throws CfnAssistException {
		for(IpPermission perm : group.getIpPermissionsEgress()) {
			vpcDiagramBuilder.addSecGroupOutboundPerms(group.getGroupId(), perm, subnetId);
		}	
	}

	private void visitInboundSecGroupPerms(VPCDiagramBuilder vpcDiagramBuilder, SecurityGroup group, String subnetId) throws CfnAssistException {
		for(IpPermission perm : group.getIpPermissions()) {
			vpcDiagramBuilder.addSecGroupInboundPerms(group.getGroupId(), perm, subnetId);
		}	
	}

	private void visitNetworkAcl(VPCDiagramBuilder vpcDiagramBuilder, NetworkAcl acl) throws CfnAssistException {
		vpcDiagramBuilder.addAcl(acl);
		String networkAclId = acl.getNetworkAclId();
		logger.debug("visit acl " + networkAclId);

		for(NetworkAclAssociation assoc : acl.getAssociations()) {
			String subnetId = assoc.getSubnetId();
			vpcDiagramBuilder.associateAclWithSubnet(acl, subnetId);
			
			for(NetworkAclEntry entry : acl.getEntries()) {
				if (entry.getEgress()) {
					vpcDiagramBuilder.addACLOutbound(networkAclId, entry, subnetId);
				} else {
					vpcDiagramBuilder.addACLInbound(networkAclId, entry, subnetId);
				}
			}			
		}	
	}

	private void visitEIP(VPCDiagramBuilder vpcDiagram, Address eip) throws CfnAssistException {
		logger.debug("visit eip " + eip.getAllocationId());

		vpcDiagram.addEIP(eip);

		String instanceId = eip.getInstanceId();
		if (instanceId!=null) {
			vpcDiagram.linkEIPToInstance(eip.getPublicIp(), instanceId);
		}	
	}

	private void visitSubnet(VPCDiagramBuilder parent, Subnet subnet) throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagram = factory.createSubnetDiagramBuilder(parent, subnet);
		
		String subnetId = subnet.getSubnetId();
		logger.debug("visit subnet " + subnetId);

		List<Instance> instances = facade.getInstancesFor(subnetId);
		for(Instance instance : instances) {
			visit(subnetDiagram, instance);
		}
		parent.add(subnetId, subnetDiagram);	
	}
	
	private void visitRouteTable(VPCDiagramBuilder vpcDiagram, RouteTable routeTable) throws CfnAssistException {
		logger.debug("visit routetable " + routeTable.getRouteTableId());
		List<Route> routes = routeTable.getRoutes();
		List<RouteTableAssociation> usersOfTable = routeTable.getAssociations();
		for (RouteTableAssociation usedBy : usersOfTable) {
			String subnetId = usedBy.getSubnetId();
			if (subnetId!=null) {
				vpcDiagram.addRouteTable(routeTable, subnetId); // possible duplication if route table reuse?
				for (Route route : routes) {
					vpcDiagram.addRoute(subnetId, route);
				}
			}
		}
 	}

	private void visit(SubnetDiagramBuilder subnetDiagram, Instance instance) throws CfnAssistException {
		logger.debug("visit instance " + instance.getInstanceId());
		subnetDiagram.add(instance);
	}

}

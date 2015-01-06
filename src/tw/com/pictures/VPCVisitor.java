package tw.com.pictures;

import java.util.List;

import javax.management.InvalidApplicationException;

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
import com.amazonaws.services.rds.model.DBSubnetGroup;

import tw.com.exceptions.CfnAssistException;

public class VPCVisitor {

	private DiagramBuilder diagramBuilder;
	private AmazonVPCFacade facade;
	private DiagramFactory factory;

	public VPCVisitor(DiagramBuilder diagramBuilder, AmazonVPCFacade facade, DiagramFactory factory) {
		this.diagramBuilder = diagramBuilder;
		this.facade = facade;
		this.factory = factory;
	}

	public void visit(Vpc vpc) throws CfnAssistException, InvalidApplicationException {	
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
		for(LoadBalancerDescription elb : facade.getLBsFor(vpcId)) {
			//visitELBFOrSecGroups(vpcDiagram, elb);
		}
		//
		for (Subnet subnet : facade.getSubnetFors(vpcId)) {
			visitSubnetForInstancesAndSecGroups(vpcDiagram, subnet);
		}
		
		diagramBuilder.add(vpcDiagram);
	}

	private void visitRDS(VPCDiagramBuilder vpcDiagram, DBInstance rds) throws CfnAssistException, InvalidApplicationException {
		vpcDiagram.addDBInstance(rds);
		DBSubnetGroup dbSubnetGroup = rds.getDBSubnetGroup();

		if (dbSubnetGroup!=null) {
			for(com.amazonaws.services.rds.model.Subnet subnet : dbSubnetGroup.getSubnets()) {
				vpcDiagram.associateDBWithSubnet(rds, subnet.getSubnetIdentifier());
			}
		}
	}

	private void visitELB(VPCDiagramBuilder vpcDiagramBuilder, LoadBalancerDescription elb) throws CfnAssistException, InvalidApplicationException {
		vpcDiagramBuilder.addELB(elb);
		for(com.amazonaws.services.elasticloadbalancing.model.Instance instance : elb.getInstances()) {
			vpcDiagramBuilder.associateELBToInstance(elb, instance.getInstanceId());
		}
		for(String subnetId : elb.getSubnets()) {
			vpcDiagramBuilder.associateELBToSubnet(elb, subnetId);
		}
	}
	
	private void visitSubnetForInstancesAndSecGroups(VPCDiagramBuilder vpcDiagramBuilder, Subnet subnet) throws CfnAssistException, InvalidApplicationException {
		String subnetId = subnet.getSubnetId();
		for(Instance instance : facade.getInstancesFor(subnetId)) {
			for(GroupIdentifier groupId : instance.getSecurityGroups()) {
				SecurityGroup group = facade.getSecurityGroupDetailsById(groupId.getGroupId());
				vpcDiagramBuilder.addSecurityGroup(group, subnetId);
				vpcDiagramBuilder.associateInstanceWithSecGroup(instance.getInstanceId(), group);
			}
		}		
	}
	
	private void visitNetworkAcl(VPCDiagramBuilder vpcDiagramBuilder, NetworkAcl acl) throws CfnAssistException, InvalidApplicationException {
		vpcDiagramBuilder.addAcl(acl);

		for(NetworkAclAssociation assoc : acl.getAssociations()) {
			String subnetId = assoc.getSubnetId();
			String networkAclId = acl.getNetworkAclId();
			vpcDiagramBuilder.associateAclWithSubnet(acl, subnetId);
			
			for(NetworkAclEntry entry : acl.getEntries()) {
				if (entry.getEgress()) {
					vpcDiagramBuilder.addOutboundRoute(networkAclId, entry, subnetId);
				} else {
					vpcDiagramBuilder.addInboundRoute(networkAclId, entry, subnetId);
				}
			}			
		}	
	}

	private void visitEIP(VPCDiagramBuilder vpcDiagram, Address eip) throws CfnAssistException, InvalidApplicationException {
		vpcDiagram.addEIP(eip);

		String instanceId = eip.getInstanceId();
		if (instanceId!=null) {
			vpcDiagram.linkEIPToInstance(eip.getPublicIp(), instanceId);
		}	
	}

	private void visitSubnet(VPCDiagramBuilder parent, Subnet subnet) throws CfnAssistException, InvalidApplicationException {
		SubnetDiagramBuilder subnetDiagram = factory.createSubnetDiagramBuilder(parent, subnet);
		
		String subnetId = subnet.getSubnetId();
		List<Instance> instances = facade.getInstancesFor(subnetId);
		for(Instance instance : instances) {
			visit(subnetDiagram, instance);
		}
		parent.add(subnetId, subnetDiagram);	
	}
	
	private void visitRouteTable(VPCDiagramBuilder vpcDiagram, RouteTable routeTable) throws CfnAssistException, InvalidApplicationException {
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

	private void visit(SubnetDiagramBuilder subnetDiagram, Instance instance) throws CfnAssistException, InvalidApplicationException {
		subnetDiagram.add(instance);
	}

}

package tw.com.pictures;

import java.util.List;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
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

	public void visit(Vpc vpc) throws CfnAssistException {	
		VPCDiagramBuilder vpcDiagram = factory.createVPCDiagramBuilder(vpc);
		String vpcId = vpc.getVpcId();
		///
		for (Subnet subnet : facade.getSubnetFors(vpcId)) {
			visit(vpcDiagram, subnet);
		}
		///
		for(RouteTable table : facade.getRouteTablesFor(vpcId)) {
			visit(vpcDiagram, table);
		}
		//
		for(Address eip : facade.getEIPFor(vpcId)) {
			visit(vpcDiagram, eip);
		}
		// 
		for(LoadBalancerDescription elb : facade.getLBFor(vpcId)) {
			visit(vpcDiagram, elb);
		}
		//
		for(DBInstance rds : facade.getRDSFor(vpcId)) {
			visit(vpcDiagram, rds);
		}
		diagramBuilder.add(vpcDiagram);
	}

	private void visit(VPCDiagramBuilder vpcDiagram, DBInstance rds) throws CfnAssistException {
		vpcDiagram.addDBInstance(rds);
		DBSubnetGroup dbSubnetGroup = rds.getDBSubnetGroup();

		if (dbSubnetGroup!=null) {
			for(com.amazonaws.services.rds.model.Subnet subnet : dbSubnetGroup.getSubnets()) {
				vpcDiagram.associateDBWithSubnet(rds, subnet.getSubnetIdentifier());
			}
		}
	}

	private void visit(VPCDiagramBuilder vpcDiagram, LoadBalancerDescription elb) throws CfnAssistException {
		vpcDiagram.addELB(elb);
		for(com.amazonaws.services.elasticloadbalancing.model.Instance instance : elb.getInstances()) {
			vpcDiagram.associateELBToInstance(elb, instance.getInstanceId());
		}
		for(String subnetId : elb.getSubnets()) {
			vpcDiagram.associateELBToSubnet(elb, subnetId);
		}
	}

	private void visit(VPCDiagramBuilder vpcDiagram, Address eip) throws CfnAssistException {
		vpcDiagram.addEIP(eip);

		String instanceId = eip.getInstanceId();
		if (instanceId!=null) {
			vpcDiagram.linkEIPToInstance(eip.getPublicIp(), instanceId);
		}	
	}

	private void visit(VPCDiagramBuilder parent, Subnet subnet) throws CfnAssistException {
		
		SubnetDiagramBuilder subnetDiagram = factory.createSubnetDiagramBuilder(parent,subnet);
		String subnetId = subnet.getSubnetId();
		List<Instance> instances = facade.getInstancesFor(subnetId);
		for(Instance instance : instances) {
			visit(subnetDiagram, instance);
		}
		parent.add(subnetId, subnetDiagram);	
	}
	
	private void visit(VPCDiagramBuilder vpcDiagram, RouteTable routeTable) throws CfnAssistException {
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
		subnetDiagram.add(instance);
		visit(subnetDiagram, instance.getSecurityGroups());
	}

	private void visit(SubnetDiagramBuilder subnetDiagram, List<GroupIdentifier> securityGroups) throws CfnAssistException {
		for(GroupIdentifier groupdId : securityGroups) {
			SecurityGroup group = facade.getSecurityGroupDetails(groupdId);
			subnetDiagram.add(group);
			visit(subnetDiagram, group.getIpPermissions(), false);
			visit(subnetDiagram, group.getIpPermissionsEgress(), true);
		}		
	}

	private void visit(SubnetDiagramBuilder subnetDiagram, List<IpPermission> ipPermissions, boolean outbound) {
		if (outbound) {
			subnetDiagram.addOutboundPerms(ipPermissions);
		} else {
			subnetDiagram.addInboundPerms(ipPermissions);
		}
	}

}

package tw.com.pictures;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Graph;
import tw.com.pictures.dot.NodesAndEdges;
import tw.com.pictures.dot.Recorder;
import tw.com.pictures.dot.Shape;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RouteTableAssociation;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSubnetGroup;

public class DescribesVPC {
	private static final Logger logger = LoggerFactory.getLogger(DescribesVPC.class);
	
	private Vpc vpc;
	private Graph networkDiagram;
	private Graph securityDiagram;
	private AmazonVPCFacade repository;
	private Recorder recorder;
	
	public DescribesVPC(Vpc vpc, AmazonVPCFacade repository, Recorder recorder) {
		this.vpc = vpc;
		this.repository = repository;
		this.recorder = recorder;
		this.networkDiagram = new Graph();
		this.securityDiagram = new Graph();		
	}

	public void walk() throws CfnAssistException   {	
		String vpcId = vpc.getVpcId();
		logger.info("Create diagrams for VPC ID:" +vpcId);

		addDiagramTitle(vpcId, networkDiagram);
		addDiagramTitle(vpcId, securityDiagram);
		
		VisitsSecGroupsAndACLs securityVisitor = new VisitsSecGroupsAndACLs(repository, vpcId, securityDiagram);
		VisitsSubnetsAndInstances subnetsAndInstances = new VisitsSubnetsAndInstances(repository, networkDiagram, securityVisitor, vpcId);	
		
		subnetsAndInstances.visit();
			
		for (RouteTable routeTable : repository.getRouteTablesFor(vpcId)) {
			walkAndRecordRouteTables(networkDiagram, routeTable, subnetsAndInstances);
		}
		
		subnetsAndInstances.labelSubnetsWithoutRouteTables();
		
		for (LoadBalancerDescription lb : repository.getLBFor(vpcId)) {
			walkLoadBalancer(networkDiagram, lb, subnetsAndInstances);
		}
		
		for (DBInstance dbInstance : repository.getRDSFor(vpcId)) {
			walkDB(networkDiagram, dbInstance, subnetsAndInstances, securityVisitor);
		}
		
		for (Address address : repository.getEIPFor(vpcId)) {
			walkEIPAddress(networkDiagram, address);
		}
	}

	private void addDiagramTitle(String vpcId, Graph diagram) {
		String title = vpcId;
		String name = AmazonVPCFacade.getNameFromTags(vpc.getTags());
		if (!name.isEmpty()) {
			title = title + String.format(" (%s)", name);
		}
		diagram.addTitle(title);
	}
	
	public void recordToFile() throws IOException {
		recordDiagramToFile("diagram_network_", recorder, networkDiagram);
		recordDiagramToFile("diagram_security_", recorder, securityDiagram);	
	}

	private void recordDiagramToFile(String prefix, Recorder recorder, Graph diagram) throws IOException {
		recorder.beginFor(vpc, prefix);
		diagram.render(recorder);
		recorder.end();
	}
	
	private void walkAndRecordRouteTables(NodesAndEdges mainDiagram, RouteTable routeTable, 
			VisitsSubnetsAndInstances subnetsAndInstances) throws CfnAssistException {
		String routeTableId = routeTable.getRouteTableId();
			
		List<RouteTableAssociation> usersOfTable = routeTable.getAssociations();
		for (RouteTableAssociation usedBy : usersOfTable) {
			String subnetId = usedBy.getSubnetId();
			if (subnetId!=null) {
				subnetsAndInstances.addRouteTable(subnetId, routeTableId);
				walkRoutesForSubnet(mainDiagram, subnetId, routeTable);	
			}	
		}	
	}

	private void walkRoutesForSubnet(NodesAndEdges diagram, String subnetId, RouteTable routeTable) {
		List<Route> routes = routeTable.getRoutes();
		for (Route route : routes) {
			String destination = getDestination(route);
			String cidr = route.getDestinationCidrBlock();
			if (!destination.equals("local")) {
				diagram.addEdge(subnetId, destination).withLabel(cidr);
			}
		}
	}
	
	private void walkEIPAddress(NodesAndEdges diagram, Address address) throws CfnAssistException {
		String publicIp = address.getPublicIp();
		diagram.addNode(publicIp).withShape(Shape.Diamond);
		String instanceId = address.getInstanceId();
		if (instanceId!=null) {
			diagram.addEdge(publicIp, instanceId);
		}
	}

	private String getDestination(Route route) {
		String dest = route.getGatewayId();
		if (dest==null) {
			dest = route.getInstanceId();
		}
		return dest;
	}
	
	private void walkLoadBalancer(NodesAndEdges diagram, LoadBalancerDescription lb, VisitsSubnetsAndInstances subnetsAndInstances) throws CfnAssistException {
		String lbName = lb.getLoadBalancerName();
		diagram.addNode(lbName).withShape(Shape.Msquare);
		
		for(com.amazonaws.services.elasticloadbalancing.model.Instance instance : lb.getInstances()) {
			String instanceId = instance.getInstanceId();
			diagram.addEdge(lbName, instanceId);
		}
		
		for(String subnet : lb.getSubnets()) {
			subnetsAndInstances.addLoadBalancer(subnet, lb);			
		}
	}
	
	private void walkDB(NodesAndEdges diagram, DBInstance dbInstance, VisitsSubnetsAndInstances subnetsAndInstances, 
			VisitsSecGroupsAndACLs securityVisitor) throws CfnAssistException {
		String rdsId = dbInstance.getDBInstanceIdentifier();
		diagram.addNode(rdsId).withShape(Shape.Octogon);
		DBSubnetGroup dbSubnetGroup = dbInstance.getDBSubnetGroup();

		if (dbSubnetGroup!=null) {
			for(com.amazonaws.services.rds.model.Subnet subnet : dbSubnetGroup.getSubnets()) {
				subnetsAndInstances.addRDS(rdsId, subnet);
				securityVisitor.walkDBSecGroups(dbInstance, subnet);
			}
		}
		
	}
	
}

package tw.com.pictures;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

public class VPCDiagramBuilder {

	private Diagram diagram;
	private Vpc vpc;
	private Map<String, SubnetDiagramBuilder> subnetDiagrams; // id -> diagram

	public VPCDiagramBuilder(Vpc vpc, DiagramFactory factory) {
		this.vpc = vpc;
		this.diagram = factory.createDiagram();
		addTitle(vpc);
		subnetDiagrams = new HashMap<String, SubnetDiagramBuilder>();
	}

	private void addTitle(Vpc vpc) {
		String title = vpc.getVpcId();
		String name = AmazonVPCFacade.getNameFromTags(vpc.getTags());
		if (!name.isEmpty()) {
			title = title + String.format(" (%s)", name);
		}
		diagram.addTitle(title);
	}

	public void add(String unique, SubnetDiagramBuilder subnetDiagram) {
		subnetDiagrams.put(unique, subnetDiagram);
	}

	public void render(Recorder recorder) throws IOException {
		recorder.beginFor(vpc, "network_diagram");	
		diagram.render(recorder);
		recorder.end();
	}

	public ChildDiagram createDiagramCluster(String id, String label) throws CfnAssistException {
		return diagram.createDiagramCluster(id, label);
	}

	public void addRouteTable(RouteTable routeTable, String subnetId) throws CfnAssistException {
		String routeTableId = routeTable.getRouteTableId();
		String name= AmazonVPCFacade.getNameFromTags(routeTable.getTags());
		if (subnetId!=null) {
			SubnetDiagramBuilder subnetDiagram = subnetDiagrams.get(subnetId);
			subnetDiagram.addRouteTable(routeTableId, name);
		} else {
			String label = AmazonVPCFacade.createLabelFromNameAndID(routeTableId, name);
			diagram.addRouteTable(routeTableId, label);
		}
	}

	public void addEIP(Address eip) throws CfnAssistException {
		String label = AmazonVPCFacade.createLabelFromNameAndID(eip.getAllocationId() ,eip.getPublicIp());
		diagram.addEIP(eip.getPublicIp(), label);	
	}

	public void linkEIPToInstance(String publicIp, String instanceId) {
		diagram.addConnectionBetween(publicIp, instanceId);			
	}

	public void addELB(LoadBalancerDescription elb) throws CfnAssistException {
		String label = elb.getLoadBalancerName();
		String id = elb.getDNSName();
		diagram.addELB(id, label);
	}

	public void associateELBToInstance(LoadBalancerDescription elb,
			String instanceId) {
		diagram.addConnectionBetween(elb.getDNSName(), instanceId);
	}

	public void associateELBToSubnet(LoadBalancerDescription elb,
			String subnetId) {
		diagram.associateWithCluster(elb.getDNSName(), subnetId, subnetDiagrams.get(subnetId));		
	}

	public void addDBInstance(DBInstance rds) throws CfnAssistException {
		String rdsId = rds.getDBInstanceIdentifier();
		String label = AmazonVPCFacade.createLabelFromNameAndID(rdsId, rds.getDBName());
		diagram.addDBInstance(rdsId, label);
	}

	public void associateRDSToSubnet(DBInstance rds, String subnetId) {
		diagram.associateWithCluster(rds.getDBInstanceIdentifier(), subnetId, subnetDiagrams.get(subnetId));	
	}

	public void addRoute(String subnetId, Route route) {
		String destination = getDestination(route);
		String cidr = route.getDestinationCidrBlock();
		if (cidr==null) {
			cidr = "no cidr";
		}
		if (!destination.equals("local")) {
			diagram.addConnectionFromCluster(destination, subnetId, subnetDiagrams.get(subnetId), cidr);
		} else {
			diagram.associateWithCluster(cidr, subnetId, subnetDiagrams.get(subnetId));
		}
		
	}
		
	private String getDestination(Route route) {
		String dest = route.getGatewayId();
		if (dest==null) {
			dest = route.getInstanceId();
		}
		return dest;
	}
}

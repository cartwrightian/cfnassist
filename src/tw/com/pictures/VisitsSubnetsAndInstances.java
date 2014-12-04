package tw.com.pictures;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.*;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

public class VisitsSubnetsAndInstances {
	public static final int SUBNET_TITLE_FONT_SIZE = 12;
	private AmazonVPCFacade facade;

	private LinkedList<String> subnetsWhereNotSeenRoutingTableYet;
	private Map<String, SubGraph> subnetNetworkClusters = new HashMap<String, SubGraph>();
	private Map<String, String> instanceNames = new HashMap<String,String>();
	private Graph networkDiagram;
	
	private String vpcId;
	private VisitsSecGroupsAndACLs securityVisitor;

	public VisitsSubnetsAndInstances(AmazonVPCFacade facade, Graph networkDiagram, VisitsSecGroupsAndACLs securityVisitor, String vpcId) {
		this.vpcId = vpcId;
		this.facade = facade;
		this.networkDiagram = networkDiagram;
		this.securityVisitor = securityVisitor;
	}
	
	public void visit() throws CfnAssistException {
		subnetsWhereNotSeenRoutingTableYet = new LinkedList<String>();
		
		for (Subnet subnet : facade.getSubnetFors(vpcId)) {
			walkSubnetAndInstances(subnet);
			subnetsWhereNotSeenRoutingTableYet.add(subnet.getSubnetId());
		}		
	}

	private void walkSubnetAndInstances(Subnet subnet) throws CfnAssistException {	
		String subnetId = subnet.getSubnetId();
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String subnetLabel = formSubnetLabel(subnet, subnetName);
		
		SubGraph subnetNetworkCluster = networkDiagram.createDiagramCluster(subnetId, subnetLabel, SUBNET_TITLE_FONT_SIZE);
		subnetNetworkClusters.put(subnetId, subnetNetworkCluster);
		
		List<Instance> instances = facade.getInstancesFor(subnetId);
		Map<Instance, String> instanceLabelPairs = new HashMap<Instance, String>();
		for(Instance instance : instances) {
			String instanceLabel = walkInstance(subnetNetworkCluster, instance, subnet);
			instanceLabelPairs.put(instance, instanceLabel);
		}

		securityVisitor.walkInstances(instanceLabelPairs, subnet);		
	}
	
	private String walkInstance(NodesAndEdges diagram, Instance instance, Subnet subnet) throws CfnAssistException {
		String instanceId = instance.getInstanceId();
		Node node = addInstanceToDiagram(diagram, instanceId, subnet.getSubnetId());
		
		String label = createInstanceLabel(instance);
		
		node.withLabel(label);
		return label;
	}

	public String createInstanceLabel(Instance instance) {
		String name = getNameForInstance(instance);
		String privateIp = instance.getPrivateIpAddress();
		String label = "";
		if (!name.isEmpty()) {
			label = String.format("%s\n(%s)", name, privateIp);
		} else {
			label = "("+privateIp+")";
		}
		return label;
	}
	
	private String getNameForInstance(Instance instance) {
		String instanceId = instance.getInstanceId();
		if  (instanceNames.containsKey(instanceId)) {
			return instanceNames.get(instanceId);
		}

		String name = AmazonVPCFacade.getNameFromTags(instance.getTags());
		if (!name.isEmpty()) {
			instanceNames.put(instanceId, name);
		}
		return name;
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.getSubnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.getSubnetId(), subnet.getCidrBlock());
	}

	private Node addInstanceToDiagram(NodesAndEdges diagram, String instanceId,
			String linkedTo) throws CfnAssistException {
		Node node = diagram.addNode(instanceId).withShape(Shape.Box);
		diagram.addEdge(instanceId, linkedTo).withDot();
		return node;
	}
	
	private String getDiagramIdFor(String subDiagramId) {
		SubGraph diagram = subnetNetworkClusters.get(subDiagramId);
		return diagram.getId();
	}

	public void addRouteTable(String subnetId, String routeTableId) throws CfnAssistException {
		subnetsWhereNotSeenRoutingTableYet.remove(subnetId);
		NodesAndEdges subnetDiagram = subnetNetworkClusters.get(subnetId);
		subnetDiagram.addNode(subnetId).addLabel(routeTableId);	
	}

	public void addLoadBalancer(String subnetId, LoadBalancerDescription description) throws CfnAssistException {
		String subnetDiagramId = getDiagramIdFor(subnetId);
		networkDiagram.addEdge(description.getLoadBalancerName(), subnetId).withDot().endsAt(subnetDiagramId);	
		securityVisitor.walkLoadBalancer(subnetId, description);
	}

	public void addRDS(String rdsId, com.amazonaws.services.rds.model.Subnet subnet) {
		String diagramId = getDiagramIdFor(subnet.getSubnetIdentifier()); // we store subnet diagrams with the subnetID as the key
		networkDiagram.addEdge(rdsId, subnet.getSubnetIdentifier()).withDot().withDottedLine().endsAt(diagramId);
	}

	public void labelSubnetsWithoutRouteTables() throws CfnAssistException {
		for(String subnetId : subnetsWhereNotSeenRoutingTableYet) {
			NodesAndEdges subnetDiagram = subnetNetworkClusters.get(subnetId);
			subnetDiagram.addNode(subnetId).addLabel("No Route Table");
		}	
	}
}

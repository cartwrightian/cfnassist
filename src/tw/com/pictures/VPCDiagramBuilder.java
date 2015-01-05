package tw.com.pictures;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import tw.com.unit.SecurityChildDiagram;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.PortRange;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RuleAction;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

public class VPCDiagramBuilder {

	private static final String CIDR_ANY = "any";
	private Diagram networkDiagram;
	private Diagram securityDiagram;

	private Vpc vpc;
	private Map<String, SubnetDiagramBuilder> subnetDiagramBuilders; // id -> diagram

	public VPCDiagramBuilder(Vpc vpc, Diagram networkDiagram, Diagram securityDiagram) {
		this.vpc = vpc;
		this.networkDiagram = networkDiagram;
		this.securityDiagram = securityDiagram;
		subnetDiagramBuilders = new HashMap<String, SubnetDiagramBuilder>();
	}

	private void addTitle(Vpc vpc, Diagram diagram) {
		String title = vpc.getVpcId();
		String name = AmazonVPCFacade.getNameFromTags(vpc.getTags());
		if (!name.isEmpty()) {
			title = title + String.format(" (%s)", name);
		}
		diagram.addTitle(title);
	}

	public void add(String unique, SubnetDiagramBuilder subnetDiagram) {
		subnetDiagramBuilders.put(unique, subnetDiagram);
	}

	public void render(Recorder recorder) throws IOException {
		renderDiagramWithPrefix(recorder, networkDiagram, "network_diagram");
		renderDiagramWithPrefix(recorder, securityDiagram, "security_diagram");
	}

	private void renderDiagramWithPrefix(Recorder recorder, Diagram diagram,
			String prefix) throws IOException {
		addTitle(vpc, diagram);
		recorder.beginFor(vpc, prefix);	
		diagram.render(recorder);
		recorder.end();
	}

	public NetworkChildDiagram createNetworkDiagramForSubnet(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String label = SubnetDiagramBuilder.formSubnetLabel(subnet, subnetName);
		return new NetworkChildDiagram(networkDiagram.createSubDiagram(subnet.getSubnetId(), label));
	}
	
	public SecurityChildDiagram createSecurityDiagramForSubnet(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String label = SubnetDiagramBuilder.formSubnetLabel(subnet, subnetName);
		return new SecurityChildDiagram(securityDiagram.createSubDiagram(subnet.getSubnetId(), label));
	}

	public void addRouteTable(RouteTable routeTable, String subnetId) throws CfnAssistException {
		if (subnetId!=null) {
			SubnetDiagramBuilder subnetDiagram = subnetDiagramBuilders.get(subnetId);
			subnetDiagram.addRouteTable(routeTable);
		} else {
			String name = AmazonVPCFacade.getNameFromTags(routeTable.getTags());
			String routeTableId = routeTable.getRouteTableId();

			String label = AmazonVPCFacade.createLabelFromNameAndID(routeTableId, name);
			networkDiagram.addRouteTable(routeTableId, label);
		}
	}

	public void addEIP(Address eip) throws CfnAssistException {
		String label = AmazonVPCFacade.createLabelFromNameAndID(eip.getAllocationId() ,eip.getPublicIp());
		networkDiagram.addPublicIPAddress(eip.getPublicIp(), label);	
	}

	public void linkEIPToInstance(String publicIp, String instanceId) {
		networkDiagram.addConnectionBetween(publicIp, instanceId);			
	}

	public void addELB(LoadBalancerDescription elb) throws CfnAssistException {
		String label = elb.getLoadBalancerName();
		String id = elb.getDNSName();
		networkDiagram.addLoadBalancer(id, label);
		securityDiagram.addLoadBalancer(id, label);
	}

	public void associateELBToInstance(LoadBalancerDescription elb,
			String instanceId) {
		networkDiagram.addConnectionBetween(elb.getDNSName(), instanceId);
	}

	public void associateELBToSubnet(LoadBalancerDescription elb,
			String subnetId) {
		networkDiagram.associateWithSubDiagram(elb.getDNSName(), subnetId, subnetDiagramBuilders.get(subnetId));		
	}

	public void addDBInstance(DBInstance rds) throws CfnAssistException {
		String rdsId = rds.getDBInstanceIdentifier();
		String label = AmazonVPCFacade.createLabelFromNameAndID(rdsId, rds.getDBName());
		networkDiagram.addDBInstance(rdsId, label);
	}
	
	public void addAcl(NetworkAcl acl) throws CfnAssistException {
		String aclId = acl.getNetworkAclId();
		String name = AmazonVPCFacade.getNameFromTags(acl.getTags());
		String label = AmazonVPCFacade.createLabelFromNameAndID(aclId,name);
		securityDiagram.addACL(aclId, label);
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void associateDBWithSubnet(DBInstance rds, String subnetId) {
		networkDiagram.associateWithSubDiagram(rds.getDBInstanceIdentifier(), subnetId, subnetDiagramBuilders.get(subnetId));	
	}
	
	public void addSecurityGroup(SecurityGroup group, String subnetId, String instanceId) throws CfnAssistException {
		subnetDiagramBuilders.get(subnetId).addSecurityGroup(group);
	}


	// this is relying on the ID being the same on both diagrams (network & security)
	public void addRoute(String subnetId, Route route) {
		String destination = getDestination(route);
		String cidr = route.getDestinationCidrBlock();
		if (cidr==null) {
			cidr = "no cidr";
		}
		if (!destination.equals("local")) {
			networkDiagram.addConnectionFromSubDiagram(destination, subnetId, subnetDiagramBuilders.get(subnetId), cidr);
		} else {
			networkDiagram.associateWithSubDiagram(cidr, subnetId, subnetDiagramBuilders.get(subnetId));
		}		
	}
		
	private String getDestination(Route route) {
		String dest = route.getGatewayId();
		if (dest==null) {
			dest = route.getInstanceId();
		}
		return dest;
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void associateAclWithSubnet(NetworkAcl acl, String subnetId) {
		securityDiagram.associateWithSubDiagram(acl.getNetworkAclId(), subnetId, subnetDiagramBuilders.get(subnetId));	
	}

	public void addOutboundRoute(String aclId, NetworkAclEntry entry, String subnetId) throws CfnAssistException {
		String cidrUniqueId = createCidrUniqueId("out", aclId, entry);
		String labelForEdge = labelFromEntry(entry);
		securityDiagram.addCidr(cidrUniqueId, getLabelFromCidr(entry));
		if (entry.getRuleAction().equals(RuleAction.Allow.toString())) {
			securityDiagram.addConnectionFromSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		} else {
			securityDiagram.addBlockedConnectionFromSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		}
	}
	
	public void addInboundRoute(String aclId, NetworkAclEntry entry, String subnetId) throws CfnAssistException {
		String cidrUniqueId = createCidrUniqueId("in", aclId, entry);
		String labelForEdge = labelFromEntry(entry);
		securityDiagram.addCidr(cidrUniqueId, getLabelFromCidr(entry));
		//  associate subnet with port range and port range with cidr
		if (entry.getRuleAction().equals(RuleAction.Allow.toString())) {
			securityDiagram.addConnectionToSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		} else {
			securityDiagram.addBlockedConnectionToSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		}
	}
	
	private String labelFromEntry(NetworkAclEntry entry) {
		String proto = getProtoFrom(entry);	
		String range = getRangeFrom(entry);
	
		return String.format("%s:[%s]\n(rule:%s)",proto, range, entry.getRuleNumber());
	}

	private String getRangeFrom(NetworkAclEntry entry) {
		PortRange portRange = entry.getPortRange();
		if (portRange==null) {
			return("all");
		} 
		return String.format("%s-%s", portRange.getFrom(), portRange.getTo());
	}

	private String getProtoFrom(NetworkAclEntry entry) {
		Integer protoNum = Integer.parseInt(entry.getProtocol());
		switch(protoNum) {
			case -1: return "all";
			case 1: return "icmp";
			case 6: return "tcp";
			case 17: return "udp";
		}
		return protoNum.toString();		
	}

	private String getLabelFromCidr(NetworkAclEntry entry) {
		String cidrBlock = entry.getCidrBlock();
		if (cidrBlock.equals("0.0.0.0/0")) {
			return CIDR_ANY;
		} 
		return cidrBlock;
	}

	private String createCidrUniqueId(String direction, String aclId, NetworkAclEntry entry) {
		String uniqueId = String.format("%s_%s_%s", direction, entry.getCidrBlock(), aclId);
		return uniqueId;
	}

}

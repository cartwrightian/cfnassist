package tw.com.pictures;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.rds.model.DBInstance;
import tw.com.AwsFacade;
import tw.com.entity.Cidr;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;

import software.amazon.awssdk.services.ec2.model.Address;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.NetworkAcl;
import software.amazon.awssdk.services.ec2.model.NetworkAclEntry;
import software.amazon.awssdk.services.ec2.model.PortRange;
import software.amazon.awssdk.services.ec2.model.Route;
import software.amazon.awssdk.services.ec2.model.RouteState;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.RuleAction;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

public class VPCDiagramBuilder extends CommonBuilder {
	private static final Logger logger = LoggerFactory.getLogger(VPCDiagramBuilder.class);

	private static final String CIDR_ANY = "any";
	private Diagram networkDiagram;
	private Diagram securityDiagram;

	private Vpc vpc;
	private Map<String, SubnetDiagramBuilder> subnetDiagramBuilders; // id -> diagram

	public VPCDiagramBuilder(Vpc vpc, Diagram networkDiagram, Diagram securityDiagram) {
		this.vpc = vpc;
		this.networkDiagram = networkDiagram;
		this.securityDiagram = securityDiagram;
		subnetDiagramBuilders = new HashMap<>();
	}

	private void addTitle(Vpc vpc, Diagram diagram) {
		String title = vpc.vpcId();
		List<Tag> tags = vpc.tags();
		String name = AmazonVPCFacade.getNameFromTags(tags);
		if (!name.isEmpty()) {
			title = title + String.format(" (%s)", name);
		}
		String project = AmazonVPCFacade.getValueFromTag(tags, AwsFacade.PROJECT_TAG);
		if (!project.isEmpty()) {
			title = title + String.format(" PROJECT=%s", project);
		}
		String env = AmazonVPCFacade.getValueFromTag(tags, AwsFacade.ENVIRONMENT_TAG);
		if (!env.isEmpty()) {
			title = title + String.format(" ENV=%s", env);
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
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.tags());
		String label = SubnetDiagramBuilder.formSubnetLabel(subnet, subnetName);
		return new NetworkChildDiagram(networkDiagram.createSubDiagram(subnet.subnetId(), label));
	}
	
	public SecurityChildDiagram createSecurityDiagramForSubnet(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.tags());
		String label = SubnetDiagramBuilder.formSubnetLabel(subnet, subnetName);
		return new SecurityChildDiagram(securityDiagram.createSubDiagram(subnet.subnetId(), label));
	}

	public void addAsssociatedRouteTable(RouteTable routeTable, String subnetId) throws CfnAssistException {
		SubnetDiagramBuilder subnetDiagram = subnetDiagramBuilders.get(subnetId);
		subnetDiagram.addRouteTable(routeTable);		
	}

	public void addEIP(Address eip) throws CfnAssistException {
		String label = AmazonVPCFacade.createLabelFromNameAndID(eip.allocationId() ,eip.publicIp());
		networkDiagram.addPublicIPAddress(eip.publicIp(), label);
	}

	public void linkEIPToInstance(String publicIp, String instanceId) {
		networkDiagram.addConnectionBetween(publicIp, instanceId);			
	}

	public void addELB(LoadBalancerDescription elb) throws CfnAssistException {
		String label = elb.loadBalancerName();
		String id = elb.dnsName();
		networkDiagram.addLoadBalancer(id, label);
		securityDiagram.addLoadBalancer(id, label);
	}

	public void associateELBToInstance(LoadBalancerDescription elb,
			String instanceId) {
		networkDiagram.addConnectionBetween(elb.dnsName(), instanceId);
	}

	public void associateELBToSubnet(LoadBalancerDescription elb,
			String subnetId) {
		networkDiagram.associateWithSubDiagram(elb.dnsName(), subnetId, subnetDiagramBuilders.get(subnetId));
		securityDiagram.associateWithSubDiagram(elb.dnsName(), subnetId, subnetDiagramBuilders.get(subnetId));
	}

	public void addDBInstance(DBInstance rds) throws CfnAssistException {
		String rdsId = rds.dbInstanceIdentifier();
		String label = AmazonVPCFacade.createLabelFromNameAndID(rdsId, rds.dbName());
		networkDiagram.addDBInstance(rdsId, label);
		securityDiagram.addDBInstance(rdsId, label);
	}
	
	public void addAcl(NetworkAcl acl) throws CfnAssistException {
		String aclId = acl.networkAclId();
		String name = AmazonVPCFacade.getNameFromTags(acl.tags());
		String label = AmazonVPCFacade.createLabelFromNameAndID(aclId,name);
		securityDiagram.addACL(aclId, label);
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void associateDBWithSubnet(DBInstance rds, String subnetId) throws CfnAssistException {
		SubnetDiagramBuilder childDiagramBuilder = subnetDiagramBuilders.get(subnetId);
		if (childDiagramBuilder==null) {
			throw new CfnAssistException("Unable to find child diagram for subnet Id:" + subnetId);
		}
		networkDiagram.associateWithSubDiagram(rds.dbInstanceIdentifier(), subnetId, childDiagramBuilder);
		securityDiagram.associateWithSubDiagram(rds.dbInstanceIdentifier(), subnetId, childDiagramBuilder);
	}
	
	public void addSecurityGroup(SecurityGroup group, String subnetId) throws CfnAssistException {
		subnetDiagramBuilders.get(subnetId).addSecurityGroup(group);
	}
	
	public void addSecGroupInboundPerms(String groupId, IpPermission ipPermsInbound, String subnetId) throws CfnAssistException {
		subnetDiagramBuilders.get(subnetId).addSecGroupInboundPerms(groupId, ipPermsInbound);		
	}
	
	public void addSecGroupOutboundPerms(String groupId ,IpPermission ipPermsOutbound, String subnetId) throws CfnAssistException {
		subnetDiagramBuilders.get(subnetId).addSecGroupOutboundPerms(groupId, ipPermsOutbound);		
	}
	
	public void addSecurityGroup(SecurityGroup dbSecurityGroup) throws CfnAssistException {
		String groupId = dbSecurityGroup.groupId();
		String label = AmazonVPCFacade.labelForSecGroup(dbSecurityGroup);
		securityDiagram.addSecurityGroup(groupId, label);	
	}
	
	public void addSecGroupInboundPerms(String groupId, IpPermission dbIpPermsInbound) throws CfnAssistException {
		addSecGroupInboundPerms(securityDiagram, groupId, dbIpPermsInbound);
	}

	public void addSecGroupOutboundPerms(String groupId, IpPermission dbIpPermsOutbound) throws CfnAssistException {
		addSecGroupOutboundPerms(securityDiagram, groupId, dbIpPermsOutbound);
	}
	
	public void associateInstanceWithSecGroup(String instanceId, SecurityGroup securityGroup) {
		securityDiagram.associate(instanceId, securityGroup.groupId());
	}

	// this is relying on the subnet ID being the same on both diagrams (network & security)
	public void addRoute(String routeTableId, String subnetId, Route route) throws CfnAssistException {
		String string = route.destinationCidrBlock();
		Cidr subnet = parseCidr(string);

		RouteState state = route.state();
		if (RouteState.ACTIVE.equals(state)) {
			addActiveRoute(routeTableId, subnetId, route, subnet);
		} else if (RouteState.BLACKHOLE.equals(state)){
			logger.warn("Route state is not active, cidr block is " + route.destinationCidrBlock());
			networkDiagram.addConnectionFromSubDiagram(RouteState.BLACKHOLE.toString(),
					subnetId, subnetDiagramBuilders.get(subnetId), string);
		} else {
			throw new CfnAssistException(String.format("Unexpected state for route with cidr %s, state was %s", string, state));
		}
	}

	private Cidr parseCidr(String string) {
		if (string==null) {
			return null;
		}
		try {
			return Cidr.parse(string);
		} catch (CfnAssistException e) {
			return null;
		}
	}

	private void addActiveRoute(String routeTableId, String subnetId, Route route, Cidr subnet) throws CfnAssistException {
		String destination = getDestination(route);
		String cidr = subnetAsCidrString(subnet);

		if (destination==null) {
			 throw new CfnAssistException("Could not find destination for " + route);
		}
		
		if (!destination.equals("local")) { 
			String diagramId = formRouteTableIdForDiagram(subnetId, routeTableId);
			networkDiagram.addRouteToInstance(destination, diagramId, subnetDiagramBuilders.get(subnetId), cidr);
		} else { 
			// this associates the cidr block with the current subnet
			networkDiagram.associateWithSubDiagram(cidr, subnetId, subnetDiagramBuilders.get(subnetId));
		}
	}

	private String subnetAsCidrString(Cidr subnet) {
		String result = "no cidr";
		if (subnet!=null) {
			result = subnet.toString();
		}
		
		return result;
	}
		
	private String getDestination(Route route) {
		// TODO Need better way of dealing with all the different types could encounter here
		String dest = route.gatewayId();
		if (dest==null) {
			dest = route.instanceId(); // api docs say this is a NAT instance, but could it be any instance?
		}
		if (dest==null) {
			dest = route.natGatewayId();
		}
		return dest;
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void associateAclWithSubnet(NetworkAcl acl, String subnetId) {
		securityDiagram.associateWithSubDiagram(acl.networkAclId(), subnetId, subnetDiagramBuilders.get(subnetId));
	}

	public void addACLOutbound(String aclId, NetworkAclEntry entry, String subnetId) throws CfnAssistException {
		String cidrUniqueId = createCidrUniqueId("out", aclId, entry);
		String labelForEdge = labelFromEntry(entry);
		securityDiagram.addCidr(cidrUniqueId, getLabelFromCidr(entry));
		if (entry.ruleAction().equals(RuleAction.ALLOW)) {
			securityDiagram.addConnectionFromSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		} else {
			securityDiagram.addBlockedConnectionFromSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		}
	}
	
	public void addACLInbound(String aclId, NetworkAclEntry entry, String subnetId) throws CfnAssistException {
		String cidrUniqueId = createCidrUniqueId("in", aclId, entry);
		String labelForEdge = labelFromEntry(entry);
		securityDiagram.addCidr(cidrUniqueId, getLabelFromCidr(entry));
		//  associate subnet with port range and port range with cidr
		if (entry.ruleAction().equals(RuleAction.ALLOW)) {
			securityDiagram.addConnectionToSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		} else {
			securityDiagram.addBlockedConnectionToSubDiagram(cidrUniqueId, subnetId, subnetDiagramBuilders.get(subnetId), labelForEdge);
		}
	}
	
	private String labelFromEntry(NetworkAclEntry entry) {
		String proto = getProtoFrom(entry);	
		String range = getRangeFrom(entry);
	
		return String.format("%s:[%s]\n(rule:%s)",proto, range, getRuleName(entry));
	}

	private String getRuleName(NetworkAclEntry entry) {
		Integer number = entry.ruleNumber();
		if (number==32767) {
			return "default";
		}
		return number.toString();
	}

	private String getRangeFrom(NetworkAclEntry entry) {
		PortRange portRange = entry.portRange();
		if (portRange==null) {
			return("all");
		}
		if (portRange.from().toString().equals(portRange.to().toString())) {
			return String.format("%s", portRange.from());
		}
		return String.format("%s-%s", portRange.from(), portRange.to());
	}

	private String getProtoFrom(NetworkAclEntry entry) {
		int protoNum = Integer.parseInt(entry.protocol());
		switch(protoNum) {
			case -1: return "all";
			case 1: return "icmp";
			case 6: return "tcp";
			case 17: return "udp";
		}
		return Integer.toString(protoNum);
	}

	private String getLabelFromCidr(NetworkAclEntry entry) {
		String cidrBlock = entry.cidrBlock();
		if (cidrBlock.equals("0.0.0.0/0")) {
			return CIDR_ANY;
		} 
		return cidrBlock;
	}

	private String createCidrUniqueId(String direction, String aclId, NetworkAclEntry entry) {
		return String.format("%s_%s_%s", direction, entry.cidrBlock(), aclId);
	}

}

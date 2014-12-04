package tw.com.pictures;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.*;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclAssociation;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.PortRange;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSecurityGroupMembership;

public class VisitsSecGroupsAndACLs {
	private static final Logger logger = LoggerFactory.getLogger(VisitsSecGroupsAndACLs.class);
	
	private AmazonVPCFacade facade;
	private Graph securityDiagram;
	private List<String> addedGroupIds;
	private List<String> rangeNodesAddedForSubnet;
	private String vpcId;
	
	// todo should have this via Diagram class
	private Map<String,SubGraph> diagramsForSubnets = new HashMap<String,SubGraph>();
	
	private List<NetworkAcl> acls = null;

	public VisitsSecGroupsAndACLs(AmazonVPCFacade facade, String vpcId, Graph securityDiagram) {
		logger.info("Create security diagram for VPC ID: " + vpcId);
		this.vpcId = vpcId;
		this.securityDiagram = securityDiagram;
		this.facade = facade;
		addedGroupIds = new LinkedList<String>();
		rangeNodesAddedForSubnet = new LinkedList<String>();
	}
	
	public void walkInstances(Map<Instance, String> instances, Subnet subnet) throws CfnAssistException {
		logger.info("visiting instances for subnet: " + subnet.getSubnetId());
		// TODO depending on order of external invocation seems very smelly
		rangeNodesAddedForSubnet.clear();
		
		SubGraph subnetSecurityCluster = getOrCreateDiagramForSubnet(subnet);
		
		String subnetId = subnet.getSubnetId(); // TODO pass in subnet instead
		for(Entry<Instance, String> entry : instances.entrySet()) {
			walkSecurityGroups(subnetSecurityCluster, entry.getKey(), entry.getValue(), subnetId);
		}
		
		walkAcls(subnetId, rangeNodesAddedForSubnet, subnetSecurityCluster);
	}

	private SubGraph getOrCreateDiagramForSubnet(Subnet subnet) {
		String subnetId = subnet.getSubnetId();
		if (diagramsForSubnets.containsKey(subnetId)) {
			return diagramsForSubnets.get(subnetId);
		}
		
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String subnetLabel = VisitsSubnetsAndInstances.formSubnetLabel(subnet, subnetName);
		
		SubGraph subnetSecurityCluster = securityDiagram.createDiagramCluster(subnetId, subnetLabel, VisitsSubnetsAndInstances.SUBNET_TITLE_FONT_SIZE);
		diagramsForSubnets.put(subnetId, subnetSecurityCluster);
		return subnetSecurityCluster;
	}

	private void walkSecurityGroups(SubGraph subnetSecurityCluster, Instance ins, String instanceLabel, String subnetId) throws CfnAssistException {			
		logger.info("Walk security groups for instance id:" + ins.getInstanceId());
		subnetSecurityCluster.addNode(instanceLabel).withShape(Shape.Diamond);
		
		for(GroupIdentifier secGroupIdent : ins.getSecurityGroups()) {
			String groupId = secGroupIdent.getGroupId();
			subnetSecurityCluster.addEdge(instanceLabel, groupId).withNoArrow().withDottedLine();
			
			if (!addedGroupIds.contains(groupId)) {
				addedGroupIds.add(groupId);
				walkSecurityGroup(subnetSecurityCluster, subnetId, secGroupIdent);
			}			
		}
	}
	
	private void walkSecurityGroup(SubGraph subnetSecurityCluster, String subnetId,  GroupIdentifier secGroupIdent) throws CfnAssistException {
		logger.info("Walk security group " + secGroupIdent);
		String groupId = secGroupIdent.getGroupId();
		
		String groupLabel = String.format("%s (%s)", secGroupIdent.getGroupName(), groupId);
		subnetSecurityCluster.addNode(groupId).withLabel(groupLabel);	
		
		SecurityGroup group = facade.getSecurityGroupDetails(secGroupIdent);
				
		walkGroupPermissions(subnetSecurityCluster, group, groupId, true, subnetId);
		walkGroupPermissions(subnetSecurityCluster, group, groupId, false, subnetId);
	}
	
	private void walkGroupPermissions(SubGraph subnetSecurityCluster, SecurityGroup group, String groupId, boolean inBound, 
			String subnetId) throws CfnAssistException {
		logger.info(String.format("Walk permissions for security group id: %s, direction: %s)", group.getGroupId(), inBound?"in":"out"));
		
		List<IpPermission> ipPermissions = (inBound? group.getIpPermissions() : group.getIpPermissionsEgress());
	
		for(IpPermission ipPermission : ipPermissions) {
			String label = createLabelFor(ipPermission);
			for(String range : ipPermission.getIpRanges()) {
				String rangeNodeId = createSubnetRangeNodeId(inBound, subnetId, range);		
				if (!rangeNodesAddedForSubnet.contains(rangeNodeId)) {
					String rangeLabel = createRangeLabel(inBound, range);
					subnetSecurityCluster.addNode(rangeNodeId).withLabel(rangeLabel);
					rangeNodesAddedForSubnet.add(rangeNodeId);
					logger.info(String.format("Added ip range %s for subnet %s", range, subnetId));
				} else {
					logger.info(String.format("Already seen range %s for subnet %s", range, subnetId));
				}
				if (inBound) {
					subnetSecurityCluster.addEdge(rangeNodeId, groupId).withLabel(label);
				} else {
					subnetSecurityCluster.addEdge(groupId, rangeNodeId).withLabel(label);
				}
			}
		}	
	}

	public void walkLoadBalancer(String subnetId, LoadBalancerDescription lbDescription) throws CfnAssistException {
		logger.info(String.format("Visit LB %s and subnet %s", lbDescription.getLoadBalancerName(), subnetId));
		List<String> secGroups = lbDescription.getSecurityGroups();
		Subnet subnet = facade.getSubnet(subnetId);
		for (String secGroupId : secGroups) {
			SecurityGroup groupDetails = facade.getSecurityGroupDetailsById(secGroupId);
			SubGraph diagram = getOrCreateDiagramForSubnet(subnet);
			walkGroupPermissions(diagram, groupDetails, groupDetails.getGroupId(), true, subnet.getSubnetId());
		}		
	}
	
	public void walkDBSecGroups(DBInstance dbInstance, com.amazonaws.services.rds.model.Subnet subnet) throws CfnAssistException {
		String subnetId = subnet.getSubnetIdentifier();
		String dbId = dbInstance.getDBInstanceIdentifier();
		
		logger.info(String.format("Adding db: %s and subnet %s", dbId, subnetId));
		SubGraph diagram = diagramsForSubnets.get(subnetId);
		List<DBSecurityGroupMembership> secGroupMembership = dbInstance.getDBSecurityGroups();
		
		logger.info(String.format("Found %s sec group memberships for %s", secGroupMembership.size(), dbId));
		
		for(DBSecurityGroupMembership membership : secGroupMembership) {
			String groupName = membership.getDBSecurityGroupName();
			GroupIdentifier groupIdentifier = getGroupIdentifierFromName(groupName);
			
			if (!addedGroupIds.contains(groupIdentifier.getGroupId())) {
				walkSecurityGroup(diagram, subnetId, groupIdentifier);
				addedGroupIds.add(groupIdentifier.getGroupId());
			} else {
				logger.info("Already seen group: " + groupIdentifier);
			}
		}	
	}

	private GroupIdentifier getGroupIdentifierFromName(String groupName) throws CfnAssistException {
		SecurityGroup group =  facade.getSecurityGroupDetailsByName(groupName);
		GroupIdentifier groupIdentifier = new GroupIdentifier();
		groupIdentifier.setGroupId(group.getGroupId());
		groupIdentifier.setGroupName(groupName);
		return groupIdentifier;
	}
	
	private void walkAcls(String subnetId, List<String> addedRangeNodes, SubGraph subnetSecurityCluster) throws CfnAssistException {	
		for(NetworkAcl acl : getAcls()) {
			for (NetworkAclAssociation assoc : acl.getAssociations()) {
				String associatedSubnetId = assoc.getSubnetId();
				if (associatedSubnetId.equals(subnetId)) {
					walkAclForSubnet(acl, associatedSubnetId, addedRangeNodes, subnetSecurityCluster);
				}
			}
		}
	}
	
	private List<NetworkAcl> getAcls() {
		if (acls==null) {
			acls = facade.getACLs(vpcId);
		}
		return acls;
	}
	
	private void walkAclForSubnet(NetworkAcl acl, String subnetId, List<String> addedRangeNodes, SubGraph subnetSecurityCluster) throws CfnAssistException {
		// TODO ACLs associated with multiple subnets?	
		List<String> aclNodeAdded = new LinkedList<String>();
		
		for(NetworkAclEntry entry : acl.getEntries()) {
			boolean inBound = !entry.isEgress();
				
			String aclNodeId = createAclNodeId(acl, entry, inBound);
			if (!aclNodeAdded.contains(aclNodeId)) {
				String labelForAclNode = createAclLabel(acl, entry, inBound);
				subnetSecurityCluster.addNode(aclNodeId).withLabel(labelForAclNode).withShape(Shape.Box);
				aclNodeAdded.add(aclNodeId);
			}		
			
			String cidrBlock = entry.getCidrBlock();
			String subnetRangeNodeId = createSubnetRangeNodeId(inBound, subnetId, cidrBlock);
			if (!addedRangeNodes.contains(subnetRangeNodeId)) {
				subnetSecurityCluster.addNode(subnetRangeNodeId).withLabel(createRangeLabel(inBound, cidrBlock));
				addedRangeNodes.add(subnetRangeNodeId);
			}
			
			String globalCidrNodeId = createRangeLabel(inBound, cidrBlock);
			Edge edgeAclToFromSubnetRange= null;
			if (inBound) {
				securityDiagram.addEdgeIgnoreDup(globalCidrNodeId, aclNodeId).withDottedLine();
				edgeAclToFromSubnetRange = subnetSecurityCluster.addEdge(aclNodeId, subnetRangeNodeId);
			} else {
				securityDiagram.addEdgeIgnoreDup(aclNodeId, globalCidrNodeId).withDottedLine();
				edgeAclToFromSubnetRange = subnetSecurityCluster.addEdge(subnetRangeNodeId, aclNodeId); 
			}
			String label = String.format("%s:%s\n(rule:%s)", createACLProtocolName(entry), createRangeLabel(entry.getPortRange()), 
					createACLRuleName(entry));
			edgeAclToFromSubnetRange.addLabel(label);
			
			if (entry.getRuleAction().equals("deny")) {
				edgeAclToFromSubnetRange.withBox();
			}		
		}
	}

	private String createLabelFor(IpPermission ipPermission) {
		int begin = -1;
		int end = -1;
		if (ipPermission.getFromPort()!=null) {
			begin = ipPermission.getFromPort();
		}
		if (ipPermission.getToPort()!=null) {
			end = ipPermission.getToPort();	
		}
		
		String range = createRangeLabel(begin, end);
		String protocol = ipPermission.getIpProtocol();
		
		String result = String.format("%s:%s", protocol, range);
		logger.info(String.format("Created label %s for permission",result));
		return result;
	}
	
	private String createAclLabel(NetworkAcl acl, NetworkAclEntry entry, boolean inBound) {
		return String.format("%s: %s\n%s", (inBound?"src":"dest"), entry.getCidrBlock(), acl.getNetworkAclId() );
	}

	private String createAclNodeId(NetworkAcl acl, NetworkAclEntry entry, boolean inBound) {
		return String.format("%s_%s_%s", (inBound?"in":"out"), acl.getNetworkAclId(), entry.getCidrBlock());
	}

	private String createACLRuleName(NetworkAclEntry entry) {
		Integer ruleNumber = entry.getRuleNumber();
		if (ruleNumber==32767) {
			return "default";
		}
		return ruleNumber.toString();
	}

	private String createACLProtocolName(NetworkAclEntry entry) {
		Integer protoNum = Integer.parseInt(entry.getProtocol());
		switch(protoNum) {
			case -1: return "all";
			case 1: return "icmp";
			case 6: return "tcp";
			case 17: return "udp";
		}
		return protoNum.toString();
	}

	private String createRangeLabel(boolean inBound, String range) {
		if (range.equals("0.0.0.0/0")) {
			range = "any";
		}
		return String.format("%s: %s", (inBound?"src":"dest"), range);
	}

	private String createSubnetRangeNodeId(boolean inBound, String subnetId, String range) {
		return String.format("%s_%s_%s", subnetId, range, (inBound?"inbound":"outbound"));
	}
	
	private String createRangeLabel(PortRange portRange) {
		if (portRange!=null) {
			return createRangeLabel(portRange.getFrom(), portRange.getTo());	
		}	
		return createRangeLabel(-1,-1);
	}

	private String createRangeLabel(int begin, int end) {
		if ((begin==-1) && (end==-1)) {
			return "all";
		}
		String range = "";
		if (begin==end) {
			range = String.format("[%s]", begin);
		} else {
			range = String.format("[%s-%s]", begin, end);
		}
		return range;
	}


}

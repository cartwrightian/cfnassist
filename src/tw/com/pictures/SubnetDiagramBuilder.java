package tw.com.pictures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InvalidApplicationException;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import tw.com.unit.SecurityChildDiagram;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;

public class SubnetDiagramBuilder implements HasDiagramId {
	private Map<String, String> instanceNames = new HashMap<String,String>(); // id -> name
	private NetworkChildDiagram networkDiagram;
	private SecurityChildDiagram securityDiagram;
	private Set<String> addedIpPerms;

	public SubnetDiagramBuilder(NetworkChildDiagram networkChildDiagram, SecurityChildDiagram securityDiagram, Subnet subnet) {
		instanceNames = new HashMap<String, String>();
		this.networkDiagram = networkChildDiagram;
		this.securityDiagram = securityDiagram;
		addedIpPerms = new HashSet<>();
	}

	public void add(Instance instance) throws CfnAssistException, InvalidApplicationException {
		String instanceId = instance.getInstanceId();
		String label = createInstanceLabel(instance);
		networkDiagram.addInstance(instanceId, label);
		securityDiagram.addInstance(instanceId, label);
	}
	
	public String createInstanceLabel(Instance instance) {
		String name = getNameForInstance(instance);
		String privateIp = instance.getPrivateIpAddress();
		String id = instance.getInstanceId();
		String label = "";
		if (!name.isEmpty()) {
			label = String.format("%s\n[%s]\n(%s)", name, id, privateIp);
		} else {
			label = String.format("[%s]\n(%s)", id, privateIp);
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

	public void addSecurityGroup(SecurityGroup group) throws CfnAssistException, InvalidApplicationException {
		String groupId = group.getGroupId();
		String name = AmazonVPCFacade.getNameFromTags(group.getTags());
		if (name.isEmpty()) {
			name = group.getGroupName();
		}
		String label = AmazonVPCFacade.createLabelFromNameAndID(groupId, name);
		securityDiagram.addSecurityGroup(groupId, label);
	}

//	public void addOutboundPerms(List<IpPermission> ipPermissions) {
//		// TODO Auto-generated method stub
//		
//	}
//
//	public void addInboundPerms(List<IpPermission> ipPermissions) {
//		// TODO Auto-generated method stub
//		
//	}

	public void render(Recorder recorder) {
		networkDiagram.render(recorder);
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.getSubnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.getSubnetId(), subnet.getCidrBlock());
	}

	public void addRouteTable(RouteTable routeTable) throws CfnAssistException {
		String name = AmazonVPCFacade.getNameFromTags(routeTable.getTags());
		String routeTableId = routeTable.getRouteTableId();
		String label = AmazonVPCFacade.createLabelFromNameAndID(routeTableId, name);
		networkDiagram.addRouteTable(routeTableId, label);
	}

	public ChildDiagram getNetworkDiagram() {
		return networkDiagram;
	}

	@Override
	public String getIdAsString() {
		return networkDiagram.getId();
	}

	public void addSecGroupInboundPerms(String groupId, IpPermission perms) throws CfnAssistException {
		String range =createRange(perms);
		String uniqueId = createUniqueId(groupId, perms, range, "in");
		if (haveAddedPerm(uniqueId)) {
			return;
		}
		
		addedIpPerms.add(uniqueId);
		String label = createLabel(perms);
		securityDiagram.addPortRange(uniqueId, range);	
		securityDiagram.connectWithLabel(uniqueId, groupId, label);
	}

	private String createRange(IpPermission perms) {
		Integer to = perms.getToPort();
		Integer from = perms.getFromPort();
		if (to.equals(from)) {
			if (to.equals(-1)) {
				return "all";
			}
			return to.toString();
		}
		return String.format("%s-%s",from, to);
	}

	public void addSecGroupOutboundPerms(String groupId, IpPermission perms) throws CfnAssistException {
		String range =createRange(perms);
		String uniqueId = createUniqueId(groupId, perms, range, "out");
		if (haveAddedPerm(uniqueId)) {
			return;
		}
		
		addedIpPerms.add(uniqueId);
		String label = createLabel(perms);
		securityDiagram.addPortRange(uniqueId, range);	
		securityDiagram.connectWithLabel(groupId, uniqueId, label);
	}

	private boolean haveAddedPerm(String uniqueId) {
		return addedIpPerms.contains(uniqueId);
	}

	private String createLabel(IpPermission perms) {
		List<String> ipRanges = perms.getIpRanges();
		if (ipRanges.isEmpty()) {
			return String.format("[%s]", perms.getIpProtocol());
		}
		
		StringBuilder rangesPart = new StringBuilder();
		for (String range : ipRanges) {
			if (rangesPart.length()!=0) {
				rangesPart.append(",\n");
			}
			rangesPart.append(range);
		}
		return String.format("(%s)\n[%s]", rangesPart.toString(),perms.getIpProtocol());
	}

	private String createUniqueId(String groupId, IpPermission perms, String range, String dir) {
		return String.format("%s_%s_%s_%s", groupId, perms.getIpProtocol(), range, dir);
	}

}

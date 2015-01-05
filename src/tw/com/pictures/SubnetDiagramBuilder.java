package tw.com.pictures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	public SubnetDiagramBuilder(NetworkChildDiagram networkChildDiagram, SecurityChildDiagram securityDiagram, Subnet subnet) {
		instanceNames = new HashMap<String, String>();
		this.networkDiagram = networkChildDiagram;
		this.securityDiagram = securityDiagram;
	}

	public void add(Instance instance) throws CfnAssistException {
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

	public void addSecurityGroup(SecurityGroup group) throws CfnAssistException {
		String groupId = group.getGroupId();
		String name = AmazonVPCFacade.getNameFromTags(group.getTags());
		if (name.isEmpty()) {
			name = group.getGroupName();
		}
		String label = AmazonVPCFacade.createLabelFromNameAndID(groupId, name);
		securityDiagram.addSecurityGroup(groupId, label);
	}

	public void addOutboundPerms(List<IpPermission> ipPermissions) {
		// TODO Auto-generated method stub
		
	}

	public void addInboundPerms(List<IpPermission> ipPermissions) {
		// TODO Auto-generated method stub
		
	}

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

}

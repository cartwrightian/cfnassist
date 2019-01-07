package tw.com.pictures;

import java.util.HashMap;
import java.util.Map;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;

import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Subnet;

public class SubnetDiagramBuilder extends CommonBuilder implements HasDiagramId  {
	private Map<String, String> instanceNames = new HashMap<String,String>(); // id -> name
	private NetworkChildDiagram networkChildDiagram;
	private SecurityChildDiagram securityChildDiagram;
	private String id;

	public SubnetDiagramBuilder(NetworkChildDiagram networkChildDiagram, SecurityChildDiagram securityDiagram, Subnet subnet) {
		instanceNames = new HashMap<String, String>();
		this.networkChildDiagram = networkChildDiagram;
		this.securityChildDiagram = securityDiagram;
		this.id = subnet.subnetId();
	}

	public void add(Instance instance) throws CfnAssistException {
		String instanceId = instance.instanceId();
		String label = createInstanceLabel(instance);
		networkChildDiagram.addInstance(instanceId, label);
		securityChildDiagram.addInstance(instanceId, label);
	}
	
	public String createInstanceLabel(Instance instance) {
		String name = getNameForInstance(instance);
		String privateIp = instance.privateIpAddress();
		String id = instance.instanceId();
		String label;
		if (!name.isEmpty()) {
			label = String.format("%s\n[%s]\n(%s)", name, id, privateIp);
		} else {
			label = String.format("[%s]\n(%s)", id, privateIp);
		}
		return label;
	}
	
	private String getNameForInstance(Instance instance) {
		String instanceId = instance.instanceId();
		if  (instanceNames.containsKey(instanceId)) {
			return instanceNames.get(instanceId);
		}

		String name = AmazonVPCFacade.getNameFromTags(instance.tags());
		if (!name.isEmpty()) {
			instanceNames.put(instanceId, name);
		}
		return name;
	}

	public void addSecurityGroup(SecurityGroup group) throws CfnAssistException {
		String groupId = group.groupId();
		String label = AmazonVPCFacade.labelForSecGroup(group);
		securityChildDiagram.addSecurityGroup(groupId, label);
	}

	public void render(Recorder recorder) {
		networkChildDiagram.render(recorder);
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.subnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.subnetId(), subnet.cidrBlock());
	}

	public void addRouteTable(RouteTable routeTable) throws CfnAssistException {
		String name = AmazonVPCFacade.getNameFromTags(routeTable.tags());
		String routeTableId = routeTable.routeTableId();
		String label = AmazonVPCFacade.createLabelFromNameAndID(routeTableId, name);
		
		String diagramIdForTable = formRouteTableIdForDiagram(id, routeTableId);
		networkChildDiagram.addRouteTable(diagramIdForTable, label);
	}

	public ChildDiagram getNetworkDiagram() {
		return networkChildDiagram;
	}

	@Override
	public String getIdAsString() {
		return networkChildDiagram.getId();
	}

	public void addSecGroupInboundPerms(String groupId, IpPermission perms) throws CfnAssistException {
		addSecGroupInboundPerms(securityChildDiagram, groupId, perms);		
	}

	public void addSecGroupOutboundPerms(String groupId, IpPermission perms) throws CfnAssistException {
		addSecGroupOutboundPerms(securityChildDiagram, groupId, perms);		
	}

}

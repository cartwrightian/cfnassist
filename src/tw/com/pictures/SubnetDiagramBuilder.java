package tw.com.pictures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;

public class SubnetDiagramBuilder {
	private Map<String, String> instanceNames = new HashMap<String,String>(); // id -> name
	private ChildDiagram  diagram;
	private String subnetId;

	public SubnetDiagramBuilder(VPCDiagramBuilder parent, Subnet subnet) throws CfnAssistException {
		instanceNames = new HashMap<String, String>();
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String subnetLabel = formSubnetLabel(subnet, subnetName);
		
		subnetId = subnet.getSubnetId();
		diagram = parent.createDiagramCluster(subnetId, subnetLabel);
	}

	public void add(Instance instance) throws CfnAssistException {
		String instanceId = instance.getInstanceId();
		String label = createInstanceLabel(instance);
		diagram.addInstance(instanceId, label);
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

	public void add(SecurityGroup group) {
		// TODO Auto-generated method stub
		
	}

	public void addOutboundPerms(List<IpPermission> ipPermissions) {
		// TODO Auto-generated method stub
		
	}

	public void addInboundPerms(List<IpPermission> ipPermissions) {
		// TODO Auto-generated method stub
		
	}

	public void render(Recorder recorder) {
		diagram.render(recorder);
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.getSubnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.getSubnetId(), subnet.getCidrBlock());
	}

	public void addRouteTable(String routeTableId, String name) throws CfnAssistException {
		String label = AmazonVPCFacade.createLabelFromNameAndID(routeTableId, name);
		diagram.addRouteTable(routeTableId, label);
	}

	public String getId() {
		return diagram.getId();
	}

}

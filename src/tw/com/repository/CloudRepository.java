package tw.com.repository;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.exceptions.CfnAssistException;
import tw.com.providers.CloudClient;

public class CloudRepository {

	private CloudClient cloudClient;
	
	private Map<String,Subnet> subnetsCache = null; // id -> Subnet
	private List<Address> addressCache = null;
	private List<SecurityGroup> groupsCache = null;
	private List<Instance> instancesCache = null;
	private List<RouteTable> routeTableCache = null;
	private List<NetworkAcl> aclsCache = null;

	private Map<String,String> subnetToVpc; // subnet id -> vpc id
	private Map<String,String> instanceToVpc; // instance id -> vpc id

	public CloudRepository(CloudClient cloudClient) {
		this.cloudClient = cloudClient;
		subnetToVpc = new HashMap<String,String>();
		instanceToVpc = new HashMap<String,String>();
	}

	public List<Subnet> getSubnetsForVpc(String vpcId) {
		loadSubnets();	
		List<Subnet> filtered = new LinkedList<Subnet>();
		for (Subnet subnet : subnetsCache.values()) {
			String subnetVpcId = subnet.getVpcId();
			if (subnetVpcId!=null) {
				if (subnetVpcId.equals(vpcId)) {
					filtered.add(subnet);
				}
			}
		}
		return filtered;
	}

	public Subnet getSubnetById(String subnetId) {
		loadSubnets();
		return subnetsCache.get(subnetId);
	}

	public List<Vpc> getAllVpcs() {
		// TODO Cache
		return cloudClient.getVpcs();
	}

	public List<Instance> getInstancesForSubnet(String subnetId) {
		loadInstances();
		List<Instance> result = new LinkedList<Instance>();

		for(Instance i : instancesCache) {
			if (i.getSubnetId().equals(subnetId)) {
				result.add(i);
			}
		}
		return result;
	}

	public SecurityGroup getSecurityGroupByName(String groupName) throws CfnAssistException {
		loadGroups();
		for(SecurityGroup group : groupsCache) {
			if (group.getGroupName().equals(groupName)) {
				return group;
			}
		}
		throw new CfnAssistException(String.format("Failed to find SecurityGroup with name '%s'", groupName));
	}

	public SecurityGroup getSecurityGroupById(String groupId) throws CfnAssistException {
		loadGroups();
		for(SecurityGroup group : groupsCache) {
			if (group.getGroupId().equals(groupId)) {
				return group;
			}
		}
		throw new CfnAssistException(String.format("Failed to find SecurityGroup with id '%s'", groupId));
	}

	public Instance getInstanceById(String instanceId) throws CfnAssistException {
		loadInstances();
		for(Instance i : instancesCache) {
			if (i.getInstanceId().equals(instanceId)) {
				return i;
			}
		}
		throw new CfnAssistException(String.format("Failed to find Instance with id '%s'", instanceId));
	}

	public List<Address> getEIPForVPCId(String vpcId) throws CfnAssistException {
		loadAddresses();
		
		// find the instance each address is associated with and then check the VPC ID on the instance
		List<Address> filtered = new LinkedList<Address>();
		for(Address address : addressCache) {
			String instanceId = address.getInstanceId();
			if (instanceId!=null) {
				String instanceVpcId = getVpcIdForInstance(instanceId);
				if (instanceVpcId.equals(vpcId)) {
					filtered.add(address);
				}
			}
		}
		return filtered;
	}

	
	private String getVpcIdForInstance(String instanceId) throws CfnAssistException {
		if (!instanceToVpc.containsKey(instanceId)) {
			Instance instance = getInstanceById(instanceId);
			String instanceVpc = instance.getVpcId();
			//String instanceVpc = getVpcForSubnet(instance.getSubnetId());
			instanceToVpc.put(instanceId, instanceVpc);	
		}			
		return instanceToVpc.get(instanceId);
	}

	public List<NetworkAcl> getALCsForVPC(String vpcId) {
		loadACLs();
		List<NetworkAcl> result = new LinkedList<NetworkAcl>();
		for (NetworkAcl acl : aclsCache) {
			if (acl.getVpcId().equals(vpcId)) {
				result.add(acl);
			}
		}
		return result;
	}

	public List<RouteTable> getRouteTablesForVPC(String vpcId) {
		loadRouteTables();
		List<RouteTable> result = new LinkedList<RouteTable>();

		for(RouteTable table : routeTableCache) {
			if (table.getVpcId().equals(vpcId)) {
				result.add(table);
			}
		}
		return result;
	}
	
	private void loadRouteTables() {
		if (routeTableCache==null) {
			routeTableCache = cloudClient.getRouteTables();
		}	
	}

	private void loadGroups() {
		if (groupsCache==null) {
			groupsCache = cloudClient.getSecurityGroups();
		}	
	}
	
	private void loadInstances() {
		if (instancesCache==null) {
			instancesCache = cloudClient.getInstances();
		}		
	}
	
	private void loadACLs() {
		if (aclsCache==null) {
			aclsCache = cloudClient.getACLs();
		}	
	}
	
	private void loadAddresses() {
		if (addressCache==null) {
			addressCache  = cloudClient.getEIPs();
		}
	}
	
	private void loadSubnets() {
		if (subnetsCache==null) {	
			
			List<Subnet> results = cloudClient.getAllSubnets();
			subnetsCache = new HashMap<String, Subnet>();
			for(Subnet subnet : results) {
				subnetsCache.put(subnet.getSubnetId(), subnet);
				String vpc = subnet.getVpcId();
				if (vpc!=null) {
					subnetToVpc.put(subnet.getSubnetId(), vpc);
				}
			}
		}
	}
}

package tw.com.repository;

import software.amazon.awssdk.services.ec2.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;
import tw.com.providers.SavesFile;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;

public class CloudRepository {
    private static final Logger logger = LoggerFactory.getLogger(CloudRepository.class);

	private CloudClient cloudClient;
	
	private Map<String,Subnet> subnetsCache = null; // id -> Subnet
	private List<Address> addressCache = null;
	private List<SecurityGroup> groupsCache = null;
	private List<Instance> instancesCache = null;
	private List<RouteTable> routeTableCache = null;
	private List<NetworkAcl> aclsCache = null;
	private Map<String, AvailabilityZone> zones = null;

	private Map<String,String> subnetToVpc; // subnet id -> vpc id
	private Map<String,String> instanceToVpc; // instance id -> vpc id

	public CloudRepository(CloudClient cloudClient) {
		this.cloudClient = cloudClient;
		subnetToVpc = new HashMap<>();
		instanceToVpc = new HashMap<>();
	}

	public List<Subnet> getSubnetsForVpc(String vpcId) {
		loadSubnets();	
		List<Subnet> filtered = new LinkedList<>();
		for (Subnet subnet : subnetsCache.values()) {
			String subnetVpcId = subnet.vpcId();
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
		List<Instance> result = new LinkedList<>();

		for(Instance i : instancesCache) {	
			String instanceSubnetId = i.subnetId();
			if (instanceSubnetId!=null) {
				if (instanceSubnetId.equals(subnetId)) {
					result.add(i);
				}
			}
		}
		return result;
	}

	public SecurityGroup getSecurityGroupByName(String groupName) throws CfnAssistException {
		loadGroups();
		for(SecurityGroup group : groupsCache) {
			if (group.groupName().equals(groupName)) {
				return group;
			}
		}
		throw new CfnAssistException(format("Failed to find SecurityGroup with name '%s'", groupName));
	}

	public SecurityGroup getSecurityGroupById(String groupId) throws CfnAssistException {
		loadGroups();
		for(SecurityGroup group : groupsCache) {
			if (group.groupId().equals(groupId)) {
				return group;
			}
		}
		throw new CfnAssistException(format("Failed to find SecurityGroup with id '%s'", groupId));
	}

	public Instance getInstanceById(String instanceId) throws CfnAssistException {
		loadInstances();
		for(Instance i : instancesCache) {
			if (i.instanceId().equals(instanceId)) {
				return i;
			}
		}
		throw new CfnAssistException(format("Failed to find Instance with id '%s'", instanceId));
	}

	public List<Address> getEIPForVPCId(String vpcId) throws CfnAssistException {
		loadAddresses();
		
		// find the instance each address is associated with and then check the VPC ID on the instance
		List<Address> filtered = new LinkedList<>();
		for(Address address : addressCache) {
			String instanceId = address.instanceId();
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
			String instanceVpc = instance.vpcId();
			//String instanceVpc = getVpcForSubnet(instance.getSubnetId());
			instanceToVpc.put(instanceId, instanceVpc);	
		}			
		return instanceToVpc.get(instanceId);
	}

	public List<NetworkAcl> getALCsForVPC(String vpcId) {
		loadACLs();
		List<NetworkAcl> result = new LinkedList<>();
		for (NetworkAcl acl : aclsCache) {
			if (acl.vpcId().equals(vpcId)) {
				result.add(acl);
			}
		}
		return result;
	}

	public List<RouteTable> getRouteTablesForVPC(String vpcId) {
		loadRouteTables();
		List<RouteTable> result = new LinkedList<>();

		for(RouteTable table : routeTableCache) {
			if (table.vpcId().equals(vpcId)) {
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
			subnetsCache = new HashMap<>();
			for(Subnet subnet : results) {
				subnetsCache.put(subnet.subnetId(), subnet);
				String vpc = subnet.vpcId();
				if (vpc!=null) {
					subnetToVpc.put(subnet.subnetId(), vpc);
				}
			}
		}
	}

    public void updateAddIpsAndPortToSecGroup(String groupId, List<InetAddress> addresses, Integer port) {
        cloudClient.addIpsToSecGroup(groupId, port, addresses);
    }

	public void updateRemoveIpsAndPortFromSecGroup(String groupId, List<InetAddress> addresses, Integer port) {
		cloudClient.deleteIpFromSecGroup(groupId, port, addresses);
	}

	public List<Tag> getTagsForInstance(String instanceId) throws WrongNumberOfInstancesException {
		Instance instance = cloudClient.getInstanceById(instanceId);
		return instance.tags();
	}

	public Map<String, AvailabilityZone> getZones() {
		initAvailabilityZones();
		return zones;
	}

	private void initAvailabilityZones() {
		if (zones==null) {
			zones = cloudClient.getAvailabilityZones();
		}
	}

	public CloudClient.AWSPrivateKey createKeyPair(String keypairName, SavesFile savesFile, Path filename) throws CfnAssistException {
        CloudClient.AWSPrivateKey privateKey = cloudClient.createKeyPair(keypairName);
        logger.info("Saving private key to " + filename);
        savesFile.save(filename, privateKey.getMaterial());
        try {
            savesFile.ownerOnlyPermisssion(filename);
        } catch (IOException ioException) {
            throw new CfnAssistException("Unable to change permission on file "+ filename, ioException);
        }
        return privateKey;
	}

	public String getIpFor(String eipAllocationId) {
        logger.info("Find EIP for " + eipAllocationId);
		List<Address> addresses = cloudClient.getEIPs();
		logger.info(format("Found %s addresses", addresses.size()));
        Stream<Address> filtered = addresses.stream().filter(address -> eipAllocationId.equals(address.allocationId()));
        Optional<Address> result = filtered.findFirst();
        result.ifPresent(found -> logger.info("Found address "+found));
        return result.isPresent() ? result.get().publicIp() : "";
	}
}

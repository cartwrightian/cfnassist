package tw.com.pictures;

import java.util.List;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.RDSClient;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

public class AmazonVPCFacade {

	private CloudRepository cloudRepository;
	private ELBRepository elbClient;
	private RDSClient rdsClient;
	
	public AmazonVPCFacade(CloudRepository cloudRepository, ELBRepository elbClient,RDSClient rdsClient) {
		this.cloudRepository = cloudRepository;
		this.elbClient = elbClient;		
		this.rdsClient = rdsClient;
	}

	public List<Vpc> getVpcs() {
		return cloudRepository.getAllVpcs();
	}

	public List<Subnet> getSubnetFors(String vpcId) {
		return cloudRepository.getSubnetsForVpc(vpcId);
	}
	
	public Subnet getSubnet(String subnetId) {
		return cloudRepository.getSubnetById(subnetId);
	}
	
	public List<Instance> getInstancesFor(String subnetId) {
		return cloudRepository.getInstancesForSubnet(subnetId);
	}
	
	public SecurityGroup getSecurityGroupDetails(GroupIdentifier groupIdentifier) throws CfnAssistException {	
		return getSecurityGroupDetailsById(groupIdentifier.getGroupId());
	}
	
	public SecurityGroup getSecurityGroupDetailsByName(String groupName) throws CfnAssistException {
		return cloudRepository.getSecurityGroupByName(groupName);
	}
	
	public SecurityGroup getSecurityGroupDetailsById(String groupId) throws CfnAssistException {
		return cloudRepository.getSecurityGroupById(groupId);	
	}
	
	public List<NetworkAcl> getACLs(String vpcId) {
		return cloudRepository.getALCsForVPC(vpcId);
	}
	
	public List<RouteTable> getRouteTablesFor(String vpcId) {
		return cloudRepository.getRouteTablesForVPC(vpcId);	
	}
	
	public List<LoadBalancerDescription> getLBsFor(String vpcId) {
		return elbClient.findELBForVPC(vpcId);
	}
	
	public List<DBInstance> getRDSFor(String vpcId) {
		return rdsClient.getDBInstancesForVpc(vpcId);
	}
	
	public List<Address> getEIPFor(String vpcId) throws CfnAssistException {
		return cloudRepository.getEIPForVPCId(vpcId);	
	}

	public static String getNameFromTags(List<Tag> tags) {
		String name = getValueFromTag(tags, "Name");
		if (name.isEmpty()) {
			name = getValueFromTag(tags, "aws:cloudformation:logical-id");
		}
		return name;
	}

	public static String getValueFromTag(List<Tag> tags, String nameOfTag) {
		for(Tag tag : tags) {
			if (tag.getKey().equals(nameOfTag)) {
				return tag.getValue();
			}
		}
		return "";
	}
	
	public static String createLabelFromNameAndID(String id, String name) {
		return String.format("%s [%s]", name, id);
	}

	public List<SecurityGroup> getSecurityGroupsFor(String vpcId) {
		return cloudRepository.getSecurityGroupsFor(vpcId);
	}

}

package tw.com.pictures;

import com.amazonaws.services.rds.model.DBInstance;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.RDSClient;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;

import java.util.List;

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

	public List<Instance> getInstancesFor(String subnetId) {
		return cloudRepository.getInstancesForSubnet(subnetId);
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
			if (tag.key().equals(nameOfTag)) {
				return tag.value();
			}
		}
		return "";
	}
	
	public static String createLabelFromNameAndID(String id, String name) {
		return String.format("%s [%s]", name, id);
	}

	public static String labelForSecGroup(SecurityGroup group) {
		String name = getNameFromTags(group.tags());
		if (name.isEmpty()) {
			name = group.groupName();
		}
		return createLabelFromNameAndID(group.groupId(), name);
	}

}

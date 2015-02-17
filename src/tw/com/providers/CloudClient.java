package tw.com.providers;

import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.WrongNumberOfInstancesException;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeNetworkAclsResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class CloudClient implements ProgressListener {
	private static final Logger logger = LoggerFactory.getLogger(CloudClient.class);

	private AmazonEC2Client ec2Client;

	public CloudClient(AmazonEC2Client ec2Client) {
		this.ec2Client = ec2Client;
	}

	public Vpc describeVpc(String vpcId) {
		logger.info("Get VPC by ID " + vpcId);
		
		DescribeVpcsRequest describeVpcsRequest = new DescribeVpcsRequest();
		Collection<String> vpcIds = new LinkedList<String>();
		vpcIds.add(vpcId);
		describeVpcsRequest.setVpcIds(vpcIds);
		DescribeVpcsResult results = ec2Client.describeVpcs(describeVpcsRequest);
		return results.getVpcs().get(0);
	}

	public List<Vpc> describeVpcs() {
		logger.info("Get All VPCs");

		DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
		return describeVpcsResults.getVpcs();
	}

	public void addTagsToResources(List<String> resources, List<Tag> tags) {
		CreateTagsRequest createTagsRequest = new CreateTagsRequest(resources, tags);	
		ec2Client.createTags(createTagsRequest);	
	}

	public void deleteTagsFromResources(List<String> resources, Tag tag) {
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest().withResources(resources).withTags(tag);
		ec2Client.deleteTags(deleteTagsRequest);	
	}

	// TODO Move into repository
	public List<Tag> getTagsForInstance(String id) throws WrongNumberOfInstancesException {
		DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(id);
		DescribeInstancesResult result = ec2Client.describeInstances(request);
		List<Reservation> res = result.getReservations();
		if (res.size()!=1) {
			throw new WrongNumberOfInstancesException(id, res.size());
		}
		List<com.amazonaws.services.ec2.model.Instance> ins = res.get(0).getInstances();
		if (ins.size()!=1) {
			throw new WrongNumberOfInstancesException(id, ins.size());
		}
		com.amazonaws.services.ec2.model.Instance instance = ins.get(0);
		List<Tag> tags = instance.getTags();
		return tags;
	}
	
	public List<Vpc> getVpcs() {
		DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
		return describeVpcsResults.getVpcs();
	}

	public List<Subnet> getAllSubnets() {
		DescribeSubnetsResult describeResults = ec2Client.describeSubnets();
		return describeResults.getSubnets();
	}

	public List<SecurityGroup> getSecurityGroups() {
		DescribeSecurityGroupsResult result = ec2Client.describeSecurityGroups();
		return result.getSecurityGroups();
	}

	public List<NetworkAcl> getACLs() {
		DescribeNetworkAclsResult result = ec2Client.describeNetworkAcls();
		return result.getNetworkAcls();		
	}
	
	public List<Instance> getInstances() {
		List<Instance> instances = new LinkedList<Instance>();
		DescribeInstancesResult result = ec2Client.describeInstances();
		List<Reservation> reservations = result.getReservations();
		for(Reservation res : reservations) {
			instances.addAll(res.getInstances());
		}
		return instances;
	}
	
	public List<RouteTable> getRouteTables() {
		DescribeRouteTablesResult result = ec2Client.describeRouteTables();
		return result.getRouteTables();	
	}

	public List<Address> getEIPs() {
		DescribeAddressesResult result = ec2Client.describeAddresses();
		return result.getAddresses();
	}

	public void addIpToSecGroup(String groupId, Integer port, InetAddress address) {
		logger.info(String.format("Add address %s for port %s to group %s", address.getHostAddress(), port.toString(), groupId));
		AuthorizeSecurityGroupIngressRequest request = new AuthorizeSecurityGroupIngressRequest();
		request.setGroupId(groupId);
		request.setIpPermissions(createPermissions(port, address));
		
		request.setGeneralProgressListener(this);
		ec2Client.authorizeSecurityGroupIngress(request);
	}
	
	public void deleteIpFromSecGroup(String groupId, Integer port, InetAddress address) {
		logger.info(String.format("Remove address %s for port %s on group %s", address.getHostAddress(), port.toString(), groupId));
		RevokeSecurityGroupIngressRequest request = new RevokeSecurityGroupIngressRequest();
		request.setGroupId(groupId);
		request.setIpPermissions(createPermissions(port, address));		
		request.setGeneralProgressListener(this);
		ec2Client.revokeSecurityGroupIngress(request );	
	}

	private Collection<IpPermission> createPermissions(Integer port,
			InetAddress address) {
		Collection<IpPermission> ipPermissions = new LinkedList<IpPermission>();
		IpPermission permission = new IpPermission();
		permission.withFromPort(port).withToPort(port).withIpProtocol("tcp").withIpRanges(String.format("%s/32", address.getHostAddress()));
		ipPermissions.add(permission);
		return ipPermissions;
	}
	
	@Override
	public void progressChanged(ProgressEvent progressEvent) {
		if (progressEvent.getEventType()==ProgressEventType.CLIENT_REQUEST_FAILED_EVENT) {
	
		}
		logger.info(progressEvent.toString());	
	}


}

package tw.com.providers;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.WrongNumberOfInstancesException;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class CloudClient {
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

}

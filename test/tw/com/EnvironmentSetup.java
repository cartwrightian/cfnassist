package tw.com;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class EnvironmentSetup {

	private static final String EXPECTED_NAME = "aws assist test VPC";
	public static Region euRegion = Region.getRegion(Regions.EU_WEST_1);

	@Test
	public void canFetchVPCToUseForTesting() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AmazonEC2Client ec2Client = createEC2Client(credentialsProvider);	
		
		Vpc found = findVPCForTesting(ec2Client);
		assertNotNull(found);
	}
	
	public static List<Subnet> getSubnetFors(AmazonEC2Client ec2Client, Vpc vpc) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<Filter> filters = new HashSet<Filter>();
		Filter vpcFilter = createVPCFilter(vpc);
		filters.add(vpcFilter);
		describeSubnetsRequest.setFilters(filters);
		
		DescribeSubnetsResult results = ec2Client.describeSubnets(describeSubnetsRequest );
		return results.getSubnets();
	}

	private static Filter createVPCFilter(Vpc vpc) {
		Filter vpcFilter = new Filter().withName("vpc-id").withValues(vpc.getVpcId());
		return vpcFilter;
	}

	public static AmazonEC2Client createEC2Client(DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonEC2Client ec2Client = new AmazonEC2Client(credentialsProvider);
		// TODO pick up from environment variable
		ec2Client.setRegion(euRegion);
		return ec2Client;
	}

	public static Vpc findVPCForTesting(AmazonEC2Client ec2Client) {
		DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
		List<Vpc> vpcs = describeVpcsResults.getVpcs();
		Vpc found = null;
		for(Vpc vpc : vpcs) {
			String possibleMatch = getNameFor(vpc);
			if (possibleMatch.equals(EXPECTED_NAME)) {
				found = vpc;
				break;
			}
		}
		return found;
	}

	private static String getNameFor(Vpc vpc) {	
		String name="";
		List<Tag> tags = vpc.getTags();
		for(Tag tag : tags) {
			if (tag.getKey().equals("Name")) {
				name = tag.getValue();
				break;
			}
		}
		return name;
	}
}

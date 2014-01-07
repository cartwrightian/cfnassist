package tw.com;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class EnvironmentSetupForTests {
	
	public static String ALT_ENV = "AdditionalTest";
	
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
		ec2Client.setRegion(TestAwsFacade.getRegion());
		return ec2Client;
	}
	
//	public static List<com.amazonaws.services.ec2.model.Tag> createExpectedEc2TagList() {
//		// TODO Auto-generated method stub
//		return null;
//	}
	

}

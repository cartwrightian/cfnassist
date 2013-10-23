package tw.com;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class VpcRepository {
	private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	
	private static final String ENVIRONMENT_TAG = "CFN_ASSIST_ENV";
	private static AmazonEC2Client ec2Client;
	
	public VpcRepository(AWSCredentialsProvider credentialsProvider, Region region) {
		ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(region);
	}
	
	public Vpc findVpcForEnv(String env) {
		logger.info(String.format("Checking for TAG %s to find VPC for environment %s", ENVIRONMENT_TAG, env));
		DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
		List<Vpc> vpcs = describeVpcsResults.getVpcs();

		for(Vpc vpc : vpcs) {
			String possibleMatch = getEnvironmentTagFor(vpc);
			if (possibleMatch.equals(env)) {
				logger.info("Found VPC with ID " + vpc.getVpcId());
				return vpc;
			}
		}
		return null;
	}

	private static String getEnvironmentTagFor(Vpc vpc) {	
		List<Tag> tags = vpc.getTags();
		for(Tag tag : tags) {
			if (tag.getKey().equals(ENVIRONMENT_TAG)) {
				return tag.getValue();
			}
		}
		return "";
	}

}

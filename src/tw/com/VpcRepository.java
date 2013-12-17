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
	
	private static AmazonEC2Client ec2Client;
	
	public VpcRepository(AWSCredentialsProvider credentialsProvider, Region region) {
		ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(region);
	}
	
	public Vpc findVpcForEnv(String project, String env) {
		logger.info(String.format("Checking for TAGs %s:%s and %s:%s to find VPC", AwsFacade.PROJECT_TAG, project, AwsFacade.ENVIRONMENT_TAG, env));
		DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
		List<Vpc> vpcs = describeVpcsResults.getVpcs();

		for(Vpc vpc : vpcs) {
			String vpcId = vpc.getVpcId();
			String possibleProject = getTagByName(vpc, AwsFacade.PROJECT_TAG);
			if (possibleProject.equals(project)) {	
				logger.debug(String.format("Found Possible VPC with %s:%s ID is %s", AwsFacade.PROJECT_TAG, possibleProject, vpcId));
				String possibleEnv = getTagByName(vpc, AwsFacade.ENVIRONMENT_TAG);
				logger.debug(String.format("Found Possible VPC with %s:%s ID is %s", AwsFacade.ENVIRONMENT_TAG, possibleEnv, vpcId));
				if (possibleEnv.equals(env)) {
					logger.info("Matched tags, vpc id is " + vpcId);
					return vpc;
				}
			}
		}
		return null;
	}

	private String getTagByName(Vpc vpc, String tagName) {
		List<Tag> tags = vpc.getTags();
		for(Tag tag : tags) {	
			if (tag.getKey().equals(tagName)) {
				return tag.getValue();
			}
		}
		return "";
	}

}

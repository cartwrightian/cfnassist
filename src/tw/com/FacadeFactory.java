package tw.com;

import org.apache.commons.cli.MissingArgumentException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class FacadeFactory {

	public AwsFacade createFacade(Region region, boolean arnMonitoring) throws MissingArgumentException {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AmazonCloudFormationClient cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(region);
		AmazonEC2Client ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(region);
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		MonitorStackEvents monitor = null;
		if (arnMonitoring) {		
			AmazonSNSClient snsClient = new AmazonSNSClient(credentialsProvider);
			snsClient.setRegion(region);
			AmazonSQSClient sqsClient = new AmazonSQSClient(credentialsProvider);
			sqsClient.setRegion(region);
			monitor = new SNSMonitor(snsClient, sqsClient);
		} else {
			monitor = new PollingStackMonitor(cfnRepository);
		}
		
		monitor.init();
		return new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository );
		
	}

}

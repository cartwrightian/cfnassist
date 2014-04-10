package tw.com;

import org.apache.commons.cli.MissingArgumentException;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class FacadeFactory {
	
	private AmazonCloudFormationClient cfnClient;
	private AmazonEC2Client ec2Client;
	private AWSCredentialsProviderChain credentialsProvider;
	private AmazonSQSClient sqsClient;
	private AmazonSNSClient snsClient;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	private AmazonElasticLoadBalancingClient elbClient;

	public FacadeFactory(Region region, String project) {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(region);
		ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(region);
		snsClient = new AmazonSNSClient(credentialsProvider);
		snsClient.setRegion(region);
		sqsClient = new AmazonSQSClient(credentialsProvider);
		sqsClient.setRegion(region);
		elbClient = new AmazonElasticLoadBalancingClient(credentialsProvider);
		elbClient.setRegion(region);
		
		cfnRepository = new CfnRepository(cfnClient, project);
		vpcRepository = new VpcRepository(ec2Client);
	}

	public AwsFacade createFacade(boolean arnMonitoring) throws MissingArgumentException {	
		
		MonitorStackEvents monitor = null;
		if (arnMonitoring) {					
			monitor = new SNSMonitor(snsClient, sqsClient);
		} else {
			monitor = new PollingStackMonitor(cfnRepository);
		}
		
		monitor.init();
		return new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);	
	}

	public ELBRepository createElbRepo() {
		return new ELBRepository(elbClient, ec2Client, vpcRepository, cfnRepository);
	}

}

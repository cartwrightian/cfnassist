package tw.com;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.ArtifactUploader;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;
import tw.com.providers.LoadBalancerClient;
import tw.com.providers.SNSEventSource;
import tw.com.repository.CfnRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class FacadeFactory {
	
	private AmazonCloudFormationClient cfnClient;
	//private AmazonEC2Client ec2Client;
	private AWSCredentialsProviderChain credentialsProvider;
	private AmazonSQSClient sqsClient;
	private AmazonSNSClient snsClient;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	private AmazonElasticLoadBalancingClient elbClient;
	private AmazonS3Client s3Client;
	private boolean arnMonitoring;
	private AwsFacade awsFacade;
	private ELBRepository elbRepository;
	private String comment;
	private ArtifactUploader artifactUploader;
	private CloudClient cloudClient;

	public FacadeFactory(Region region, String project,boolean arnMonitoring) {
		this.arnMonitoring = arnMonitoring;
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(region);
		AmazonEC2Client ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(region);
		snsClient = new AmazonSNSClient(credentialsProvider);
		snsClient.setRegion(region);
		sqsClient = new AmazonSQSClient(credentialsProvider);
		sqsClient.setRegion(region);
		elbClient = new AmazonElasticLoadBalancingClient(credentialsProvider);
		elbClient.setRegion(region);
		s3Client = new AmazonS3Client(credentialsProvider);
		s3Client.setRegion(region);
		
		cloudClient = new CloudClient(ec2Client);
		cfnRepository = new CfnRepository(new CloudFormationClient(cfnClient), cloudClient, project);
		vpcRepository = new VpcRepository(cloudClient);
	}

	public AwsFacade createFacade() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		if (awsFacade==null) {
			SNSEventSource eventSource = new SNSEventSource(snsClient, sqsClient);
			MonitorStackEvents monitor = null;
			if (arnMonitoring) {	
				monitor = new SNSMonitor(eventSource, cfnRepository);
			} else {
				monitor = new PollingStackMonitor(cfnRepository);
			}
			
			monitor.init();
			awsFacade = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository);
			if (comment!=null) {
				awsFacade.setCommentTag(comment);
			}
		}
		
		return awsFacade;	
	}

	public ELBRepository createElbRepo() {
		if (elbRepository==null) {
			LoadBalancerClient loadBalancerClient = new LoadBalancerClient(elbClient);
			elbRepository = new ELBRepository(loadBalancerClient, vpcRepository, cfnRepository);
		}
		
		return elbRepository;
	}

	public AmazonS3Client getS3Client() {
		return s3Client;
	}

	public void setCommentTag(String comment) {
		this.comment = comment;	
	}

	public ArtifactUploader createArtifactUploader(ProjectAndEnv projectAndEnv) {
		if (artifactUploader==null) {
			artifactUploader = new ArtifactUploader(s3Client, projectAndEnv.getS3Bucket(),
					projectAndEnv.getBuildNumber());
		}
		return artifactUploader;
	}

}

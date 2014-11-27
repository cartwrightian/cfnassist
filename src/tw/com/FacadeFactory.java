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
	private String comment;
	private boolean snsMonitoring;
	private Region region;
	private String project;
	
	private boolean init;

	// amazon apis
	private AWSCredentialsProviderChain credentialsProvider;
	private AmazonCloudFormationClient cfnClient;
	private AmazonSQSClient sqsClient;
	private AmazonSNSClient snsClient;
	private AmazonS3Client s3Client;
	private AmazonEC2Client ec2Client;
	private AmazonElasticLoadBalancingClient elbClient;
	
	// providers
	private ArtifactUploader artifactUploader;
	private CloudClient cloudClient;
	private CloudFormationClient formationClient;
	private LoadBalancerClient loadBalancerClient;
	
	// repo
	private ELBRepository elbRepository;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	
	// controller
	private AwsFacade awsFacade;

	public FacadeFactory() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();	
	}
	
	public void setRegion(Region awsRegion) {
		this.region = awsRegion;
	}
	
	public void setProject(String project) {
		this.project = project;
	}
	
	public void setSNSMonitoring(Boolean snsMonitoring) {
		this.snsMonitoring = snsMonitoring;		
	}
	
	public void setCommentTag(String comment) {
		this.comment = comment;	
	}

	private void init() {
		if (!init) {
			createAmazonAPIClients();	
			createProviders();
			createRepo();
			init = true;
		}
	}
	
	private void createProviders() {
		loadBalancerClient = new LoadBalancerClient(elbClient);
		cloudClient = new CloudClient(ec2Client);
		formationClient = new CloudFormationClient(cfnClient);
	}

	private void createRepo() {	
		cfnRepository = new CfnRepository(formationClient, cloudClient, project);
		vpcRepository = new VpcRepository(cloudClient);
		elbRepository = new ELBRepository(loadBalancerClient, vpcRepository, cfnRepository);
	}

	private void createAmazonAPIClients() {
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
		s3Client = new AmazonS3Client(credentialsProvider);
		s3Client.setRegion(region);
	}

	public AwsFacade createFacade() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		if (awsFacade==null) {
			init();
			SNSEventSource eventSource = new SNSEventSource(snsClient, sqsClient);
			MonitorStackEvents monitor = null;
			if (snsMonitoring) {	
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

	public AmazonS3Client getS3Client() {
		return s3Client;
	}

	public ArtifactUploader createArtifactUploader(ProjectAndEnv projectAndEnv) {
		if (artifactUploader==null) {
			artifactUploader = new ArtifactUploader(s3Client, projectAndEnv.getS3Bucket(),
					projectAndEnv.getBuildNumber());
		}
		return artifactUploader;
	}

}

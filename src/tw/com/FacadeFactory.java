package tw.com;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.providers.*;
import tw.com.repository.CfnRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

public class FacadeFactory {
	private boolean snsMonitoring = false;
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
	private AmazonRDSClient rdsClient;
	private AmazonIdentityManagementClient iamClient;
	
	// providers
	private ArtifactUploader artifactUploader;
	private CloudClient cloudClient;
	private CloudFormationClient formationClient;
	private LoadBalancerClient loadBalancerClient;
	private RDSClient datastoreClient;
	private SNSNotificationSender notificationSender;
    private SavesFile savesFile;

    // repo
	private ELBRepository elbRepository;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	private CloudRepository cloudRepository;
	
	// controller
	private AwsFacade awsFacade;
	private DiagramCreator diagramCreator;
	private IdentityProvider identityProvider;

    public FacadeFactory() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();	
	}
	
	public void setRegion(Region awsRegion) {
		this.region = awsRegion;
	}
	
	public void setProject(String project) {
		this.project = project;
	}
	
	public void setSNSMonitoring() {
		this.snsMonitoring = true;		
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
		datastoreClient = new RDSClient(rdsClient);
		notificationSender = new SNSNotificationSender(snsClient);
		identityProvider = new IdentityProvider(iamClient);
	}

	private void createRepo() {	
		cloudRepository = new CloudRepository(cloudClient);
		cfnRepository = new CfnRepository(formationClient, cloudRepository, project);
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
		rdsClient = new AmazonRDSClient(credentialsProvider);
		rdsClient.setRegion(region);
		iamClient = new AmazonIdentityManagementClient(credentialsProvider);
		iamClient.setRegion(region);
	}

	public AwsFacade createFacade() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		if (awsFacade==null) {
			init();
			SNSEventSource eventSource = new SNSEventSource(snsClient, sqsClient);
			MonitorStackEvents monitor;
			if (snsMonitoring) {	
				monitor = new SNSMonitor(eventSource, cfnRepository);
			} else {
				monitor = new PollingStackMonitor(cfnRepository);
			}
			
			monitor.init();
			awsFacade = new AwsFacade(monitor, cfnRepository, vpcRepository, elbRepository, 
					cloudRepository, notificationSender, identityProvider, region.getName());
		}	
		return awsFacade;	
	}

    public ArtifactUploader createArtifactUploader(ProjectAndEnv projectAndEnv) {
		init();
		if (artifactUploader==null) {
			artifactUploader = new ArtifactUploader(s3Client, projectAndEnv.getS3Bucket(),
					projectAndEnv.getBuildNumber());
		}
		return artifactUploader;
	}

	public DiagramCreator createDiagramCreator() {
		init();
		if (diagramCreator==null) {
			AmazonVPCFacade amazonVpcFacade = new AmazonVPCFacade(cloudRepository, elbRepository, datastoreClient);
			diagramCreator = new DiagramCreator(amazonVpcFacade);
		}
		return diagramCreator;
	}

	public ProvidesCurrentIp getCurrentIpProvider() {
		return new ProvidesCurrentIp();
	}

	public SavesFile getSavesFile() {
        if (savesFile==null) {
            savesFile = new SavesFile();
        }
        return savesFile;
	}
}

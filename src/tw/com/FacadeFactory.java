package tw.com;

import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.apache.commons.cli.MissingArgumentException;
import org.joda.time.DateTime;
import software.amazon.awssdk.services.ec2.Ec2Client;
import tw.com.commandline.CommandExecutor;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.providers.*;
import tw.com.repository.*;

public class FacadeFactory implements ProvidesNow {
	private boolean snsMonitoring = false;
	private String project;
	
	private boolean init;

    private AmazonCloudFormation cfnClient;
	private AmazonSQS sqsClient;
	private AmazonSNS snsClient;
	private AmazonS3 s3Client;
	private Ec2Client ec2Client;
	private AmazonElasticLoadBalancing elbClient;
	private AmazonRDS rdsClient;
	private AmazonIdentityManagement iamClient;
    private AWSLogs awsLogClient;


    // providers
	private ArtifactUploader artifactUploader;
	private CloudClient cloudClient;
	private CloudFormationClient formationClient;
	private LoadBalancerClient loadBalancerClient;
	private RDSClient datastoreClient;
	private SNSNotificationSender notificationSender;
    private SavesFile savesFile;
    private LogClient logClient;

    // repo
	private ELBRepository elbRepository;
	private CfnRepository cfnRepository;
	private VpcRepository vpcRepository;
	private CloudRepository cloudRepository;
    private LogRepository logRepository;

    // controller
	private AwsFacade awsFacade;
	private DiagramCreator diagramCreator;
	private IdentityProvider identityProvider;

    public FacadeFactory() {
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
        AwsRegionProvider regionProvider = new DefaultAwsRegionProviderChain();
        cloudClient = new CloudClient(ec2Client, regionProvider);
		formationClient = new CloudFormationClient(cfnClient);
		datastoreClient = new RDSClient(rdsClient);
		notificationSender = new SNSNotificationSender(snsClient);
		identityProvider = new IdentityProvider(iamClient);
		logClient = new LogClient(awsLogClient);
	}

	private void createRepo() {	
		cloudRepository = new CloudRepository(cloudClient);
		cfnRepository = new CfnRepository(formationClient, cloudRepository, project);
		vpcRepository = new VpcRepository(cloudClient);
		elbRepository = new ELBRepository(loadBalancerClient, vpcRepository, cfnRepository);
		logRepository = new LogRepository(logClient, this, getSavesFile());
	}

	private void createAmazonAPIClients() {
        cfnClient = AmazonCloudFormationClientBuilder.defaultClient();
        ec2Client = Ec2Client.builder().build();
        snsClient = AmazonSNSClientBuilder.defaultClient();
        sqsClient = AmazonSQSClientBuilder.defaultClient();
        elbClient = AmazonElasticLoadBalancingClientBuilder.defaultClient();
        s3Client = AmazonS3ClientBuilder.defaultClient();
        rdsClient = AmazonRDSClientBuilder.defaultClient();
        iamClient = AmazonIdentityManagementClientBuilder.defaultClient();
        awsLogClient = AWSLogsClientBuilder.defaultClient();
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
					cloudRepository, notificationSender, identityProvider, logRepository);
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

	public CommandExecutor getCommandExecutor() {
		return new CommandExecutor();
	}

    @Override
    public DateTime getNow() {
        return DateTime.now();
    }
}

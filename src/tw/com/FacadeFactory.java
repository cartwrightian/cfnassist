package tw.com;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tw.com.commandline.CommandExecutor;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.providers.*;
import tw.com.repository.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class FacadeFactory implements ProvidesNow {
	private boolean snsMonitoring = false;
	private String project;
	
	private boolean init;

    private CloudFormationClient cfnClient;
	private SqsClient sqsClient;
	private SnsClient snsClient;
	private S3Client s3Client;
	private Ec2Client ec2Client;
	private ElasticLoadBalancingClient elbClient;
	private RdsClient rdsClient;
	private IamClient iamClient;
    private CloudWatchLogsClient awsLogClient;

    // providers
	private ArtifactUploader artifactUploader;
	private CloudClient cloudClient;
	private CFNClient formationClient;
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
		formationClient = new CFNClient(cfnClient);
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
        cfnClient = CloudFormationClient.create();
        ec2Client = Ec2Client.create();
        snsClient = SnsClient.create();
        sqsClient = SqsClient.create();
        elbClient = ElasticLoadBalancingClient.create();
        s3Client = S3Client.create();
        rdsClient = RdsClient.create();
        awsLogClient = CloudWatchLogsClient.create();
		iamClient = IamClient.builder().
				credentialsProvider(DefaultCredentialsProvider.create())
				.region(Region.AWS_GLOBAL).
						build();
	}

	public AwsFacade createFacade() throws MissingArgumentException, CfnAssistException, InterruptedException {		
		if (awsFacade==null) {
			init();
			SNSEventSource eventSource = new SNSEventSource(snsClient, sqsClient);
			MonitorStackEvents monitor;
			if (snsMonitoring) {	
				monitor = new SNSMonitor(eventSource, cfnRepository, cfnRepository);
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
    public ZonedDateTime getUTCNow() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }
}

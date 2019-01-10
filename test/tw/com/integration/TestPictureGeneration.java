package tw.com.integration;

import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.rds.RdsClient;
import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.CloudClient;
import tw.com.providers.CFNClient;
import tw.com.providers.LoadBalancerClient;
import tw.com.providers.RDSClient;
import tw.com.repository.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestPictureGeneration {

	private RDSClient rdsClient;
	private CloudRepository cloudRepository;
	private ELBRepository elbRepository;
	
	@Before
	public void beforeEachTestRuns() {
		Ec2Client ec2Client = EnvironmentSetupForTests.createEC2Client();
		ElasticLoadBalancingClient awsElbClient = EnvironmentSetupForTests.createELBClient();
		software.amazon.awssdk.services.cloudformation.CloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient();
		RdsClient awsRdsClient = EnvironmentSetupForTests.createRDSClient();

		CloudClient cloudClient = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());
		LoadBalancerClient elbClient = new LoadBalancerClient(awsElbClient);
		VpcRepository vpcRepository = new VpcRepository(cloudClient);

		CFNClient CFNClient = new CFNClient(cfnClient);
		cloudRepository = new CloudRepository(cloudClient);
		ResourceRepository cfnRepository = new CfnRepository(CFNClient, cloudRepository, "CfnAssist");
		
		elbRepository = new ELBRepository(elbClient, vpcRepository, cfnRepository);
		rdsClient = new RDSClient(awsRdsClient);
	}

	@Test
	public void shouldGenerateDiagramFromCurrentAccountVPCs() throws IOException, CfnAssistException {
		Path folder = Paths.get(".").toAbsolutePath();
		Recorder recorder = new FileRecorder(folder);
		
		AmazonVPCFacade awsFacade = new AmazonVPCFacade(cloudRepository, elbRepository, rdsClient);
		DiagramCreator createsDiagrams = new DiagramCreator(awsFacade);
		createsDiagrams.createDiagrams(recorder);
	}
	
}

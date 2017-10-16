package tw.com.integration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.rds.AmazonRDS;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;
import tw.com.providers.LoadBalancerClient;
import tw.com.providers.RDSClient;
import tw.com.repository.CfnRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.ResourceRepository;
import tw.com.repository.VpcRepository;

public class TestPictureGeneration {

	private RDSClient rdsClient;
	private CloudRepository cloudRepository;
	private ELBRepository elbRepository;
	
	@Before
	public void beforeEachTestRuns() {
		AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AmazonEC2 ec2Client = EnvironmentSetupForTests.createEC2Client();
		AmazonElasticLoadBalancing awsElbClient = EnvironmentSetupForTests.createELBClient();
		AmazonCloudFormation cfnClient = EnvironmentSetupForTests.createCFNClient();
		AmazonRDS awsRdsClient = EnvironmentSetupForTests.createRDSClient();

		CloudClient cloudClient = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());
		LoadBalancerClient elbClient = new LoadBalancerClient(awsElbClient);
		VpcRepository vpcRepository = new VpcRepository(cloudClient);

		CloudFormationClient cloudFormationClient = new CloudFormationClient(cfnClient);
		cloudRepository = new CloudRepository(cloudClient);
		ResourceRepository cfnRepository = new CfnRepository(cloudFormationClient, cloudRepository, "CfnAssist");
		
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

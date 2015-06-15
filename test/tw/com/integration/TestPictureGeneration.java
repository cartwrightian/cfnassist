package tw.com.integration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.rds.AmazonRDSClient;

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
		AmazonEC2Client ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		AmazonElasticLoadBalancingClient awsElbClient = EnvironmentSetupForTests.createELBClient(credentialsProvider);
		AmazonCloudFormationClient cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		AmazonRDSClient awsRdsClient = EnvironmentSetupForTests.createRDSClient(credentialsProvider);

		CloudClient cloudClient = new CloudClient(ec2Client);	
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

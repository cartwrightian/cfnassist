package tw.com.integration;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.AwsFacade;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.providers.CloudFormationClient;
import tw.com.repository.CfnRepository;
import tw.com.repository.VpcRepository;

public class TestCloudFormationClient {
	
	private static final String STACK_NAME = "CfnAssistTestsubnetWithCIDRParam";
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client directClient;
	private static VpcRepository vpcRepository;
	CloudFormationClient formationClient;
	private DeletesStacks deletesStacks;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private Vpc mainTestVPC;
	private AwsFacade awsProvider;
	
	@BeforeClass
	public static void onceBeforeClassRuns() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		vpcRepository = new VpcRepository(directClient);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		
		new DeletesStacks(cfnClient).ifPresent(STACK_NAME).act();
	}
	
	@Rule public TestName test = new TestName();
	
	@Before
	public void beforeEachTestRuns() {
		formationClient = new CloudFormationClient(cfnClient);
		CfnRepository cfnRepository = new CfnRepository(formationClient, EnvironmentSetupForTests.PROJECT);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository );
		awsProvider = new AwsFacade(monitor , cfnRepository, vpcRepository);
		awsProvider.setCommentTag(test.getMethodName());
		
		deletesStacks = new DeletesStacks(cfnClient);
		mainTestVPC = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
	}
	
	@After
	public void afterEachTestHasRun() {
		deletesStacks.act();
	}
	
	@Test
	public void shouldQueryCreatedStack() throws FileNotFoundException, IOException, InvalidParameterException, CfnAssistException, InterruptedException {
		String vpcIdA = mainTestVPC.getVpcId();
		String cidrA = "10.0.10.0/24";
	
		StackNameAndId stackA = invokeSubnetCreation(cidrA, mainProjectAndEnv);	
		deletesStacks.ifPresent(stackA); // mark for deletion		
		
		// all stacks
		List<Stack> resultStacks = formationClient.describeAllStacks();
		assertTrue(resultStacks.size()>0);
		boolean seen = false;
		for(Stack candidate : resultStacks) {
			if (candidate.getStackName().equals(STACK_NAME)) {
				seen = true;
				break;
			}
		}
		assertTrue(seen);
		
		// single stack
		Stack resultStack = formationClient.describeStack(STACK_NAME);
		assertEquals(STACK_NAME, resultStack.getStackName());
		
		// events
		List<StackEvent> resultEvents = formationClient.describeStackEvents(STACK_NAME);
		assertTrue(resultEvents.size()>0); // TODO what can we check here?
						
		// resources
		List<StackResource> resultResources = formationClient.describeStackResources(stackA.getStackName());
		assertEquals(1, resultResources.size());
				
		String physicalIdA = resultResources.get(0).getPhysicalResourceId();
		// fetch the subnet id directly
		DescribeSubnetsResult subnetResultsA = getSubnetDetails(physicalIdA );
		
		// validate the subnets have the expected address block and is from the correct VPC
		assertEquals(1, subnetResultsA.getSubnets().size());	
		Subnet subnetA = subnetResultsA.getSubnets().get(0);
		assertEquals(cidrA, subnetA.getCidrBlock());
		assertEquals(vpcIdA, subnetA.getVpcId());		
	}
	
	private DescribeSubnetsResult getSubnetDetails(String physicalId) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<String> subnetIds = new LinkedList<String>();
		subnetIds.add(physicalId);
		describeSubnetsRequest.setSubnetIds(subnetIds);
		DescribeSubnetsResult result = directClient.describeSubnets(describeSubnetsRequest);
		return result;
	}

	private StackNameAndId invokeSubnetCreation(String cidr, ProjectAndEnv projectAndEnv)
			throws FileNotFoundException, IOException,
			InvalidParameterException, CfnAssistException, InterruptedException {
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		Parameter cidrParameter = new Parameter();
		cidrParameter.setParameterKey("cidr");
		cidrParameter.setParameterValue(cidr);
		parameters.add(cidrParameter);
		File templateFile = new File(FilesForTesting.SUBNET_CIDR_PARAM);
		return awsProvider.applyTemplate(templateFile, projectAndEnv, parameters );
	}

}

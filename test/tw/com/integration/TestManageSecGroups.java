package tw.com.integration;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import tw.com.EnvironmentSetupForTests;
import tw.com.providers.CloudClient;

public class TestManageSecGroups {
	
	private static final String GROUP_NAME = "TestManageSecGroups";
	private CloudClient client;
	private static String groupId = "";
	private static AmazonEC2Client ec2Client;

	@BeforeClass
	public static void onceBeforeAllTestsRuns() {
		AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
	}
	
	@Before
	public void beforeEachTestRuns() {	
		client = new CloudClient(ec2Client);
		
		deleteGroupIfPresent();
		
		CreateSecurityGroupRequest createRequest = new CreateSecurityGroupRequest().
				withDescription("test group").
				withGroupName(GROUP_NAME);
		CreateSecurityGroupResult result = ec2Client.createSecurityGroup(createRequest);
		groupId = result.getGroupId();
	}

	private static void deleteGroupIfPresent() {
		try {	
			DescribeSecurityGroupsRequest describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest().withGroupNames(GROUP_NAME);
			DescribeSecurityGroupsResult existing = ec2Client.describeSecurityGroups(describeSecurityGroupsRequest);
			if (existing.getSecurityGroups().size()>0) {
				DeleteSecurityGroupRequest deleteGroup = new DeleteSecurityGroupRequest().withGroupName(GROUP_NAME);
				ec2Client.deleteSecurityGroup(deleteGroup);	
			}
		} catch (AmazonServiceException exception) {
			// no op
		}
	}
	
	@AfterClass
	public static void afterAllTestsRun() {
		deleteGroupIfPresent();
	}

	@Test
	public void testShouldAddAndDeleteAnIpToASecurityGroup() {
		Integer port = 8080;
		String cidr = "192.168.0.1/32";
		client.addIpToSecGroup(groupId, port , cidr);
		
		DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest().withGroupIds(groupId);
		DescribeSecurityGroupsResult result = ec2Client.describeSecurityGroups(request);
		
		List<SecurityGroup> securityGroups = result.getSecurityGroups();
		assertEquals(1, securityGroups.size());
		SecurityGroup group = securityGroups.get(0);
		
		List<IpPermission> perms = group.getIpPermissions();
		assertEquals(1, perms.size());
		
		IpPermission ipPermission = perms.get(0);
		assertEquals(port, ipPermission.getToPort());
		assertEquals(port, ipPermission.getFromPort());
		assertEquals(1, ipPermission.getIpRanges().size());
		assertEquals(cidr, ipPermission.getIpRanges().get(0));
		
		client.deleteIpFromSecGroup(groupId, port, cidr);
		
		result = ec2Client.describeSecurityGroups(request);
		securityGroups = result.getSecurityGroups();
		assertEquals(1, securityGroups.size());
		group = securityGroups.get(0);
		perms = group.getIpPermissions();
		assertEquals(0, perms.size());
	}
	
}

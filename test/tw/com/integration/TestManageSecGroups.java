package tw.com.integration;

import static org.junit.Assert.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.ec2.AmazonEC2;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
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
	private static AmazonEC2 ec2Client;

	@BeforeClass
	public static void onceBeforeAllTestsRuns() {
        ec2Client = EnvironmentSetupForTests.createEC2Client();
	}
	
	@Before
	public void beforeEachTestRuns() {	
		client = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());
		
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
	public void testShouldAddAndDeleteAnIpsToASecurityGroup() throws UnknownHostException {
		Integer port = 8080;
        List<InetAddress> addresses = new ArrayList<>();
        InetAddress addressA = Inet4Address.getByName("192.168.0.1");
        InetAddress addressB = Inet4Address.getByName("192.168.0.2");
        addresses.add(addressA);
        addresses.add(addressB);

        String cidrA = "192.168.0.1/32";
        String cidrB = "192.168.0.2/32";

        //add
        client.addIpsToSecGroup(groupId, port , addresses);
		
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
		assertEquals(2, ipPermission.getIpv4Ranges().size());
		assertEquals(cidrA, ipPermission.getIpv4Ranges().get(0).getCidrIp());
        assertEquals(cidrB, ipPermission.getIpv4Ranges().get(1).getCidrIp());

        //remove
		client.deleteIpFromSecGroup(groupId, port, addresses);
		
		result = ec2Client.describeSecurityGroups(request);
		securityGroups = result.getSecurityGroups();
		assertEquals(1, securityGroups.size());
		group = securityGroups.get(0);
		perms = group.getIpPermissions();
		assertEquals(0, perms.size());
	}
	
}

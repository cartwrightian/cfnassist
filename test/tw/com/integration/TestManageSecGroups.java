package tw.com.integration;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.CloudClient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestManageSecGroups {

    private static final String GROUP_NAME = "TestManageSecGroups";
    private CloudClient client;
    private static String groupId = "";
    private static Ec2Client ec2Client;

    @BeforeClass
    public static void onceBeforeAllTestsRuns() {
        ec2Client = EnvironmentSetupForTests.createEC2Client();
    }

    @Before
    public void beforeEachTestRuns() {
        client = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());

        deleteGroupIfPresent();

        CreateSecurityGroupRequest createRequest = CreateSecurityGroupRequest.builder().
                description("test group").
                groupName(GROUP_NAME).build();
        CreateSecurityGroupResponse result = ec2Client.createSecurityGroup(createRequest);
        groupId = result.groupId();
    }

    private static void deleteGroupIfPresent() {
        try {
            DescribeSecurityGroupsRequest describeSecurityGroupsRequest = DescribeSecurityGroupsRequest.
                    builder().groupNames(GROUP_NAME).build();

            DescribeSecurityGroupsResponse existing = ec2Client.describeSecurityGroups(describeSecurityGroupsRequest);
            if (existing.securityGroups().size()>0) {
                DeleteSecurityGroupRequest deleteGroup = DeleteSecurityGroupRequest.builder().groupName(GROUP_NAME).build();
                ec2Client.deleteSecurityGroup(deleteGroup);
            }
        } catch (Ec2Exception exception) {
            // no op - thrown if group does not exist
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

        DescribeSecurityGroupsRequest request = DescribeSecurityGroupsRequest.builder().groupIds(groupId).build();
        DescribeSecurityGroupsResponse result = ec2Client.describeSecurityGroups(request);

        List<SecurityGroup> securityGroups = result.securityGroups();
        assertEquals(1, securityGroups.size());
        SecurityGroup group = securityGroups.get(0);

        List<IpPermission> perms = group.ipPermissions();
        assertEquals(1, perms.size());

        IpPermission ipPermission = perms.get(0);
        assertEquals(port, ipPermission.toPort());
        assertEquals(port, ipPermission.fromPort());
        List<IpRange> ipRanges = ipPermission.ipRanges();
        assertEquals(2, ipRanges.size());
        assertEquals(cidrA, ipRanges.get(0).cidrIp());
        assertEquals(cidrB, ipRanges.get(1).cidrIp());

        //remove
        client.deleteIpFromSecGroup(groupId, port, addresses);

        result = ec2Client.describeSecurityGroups(request);
        securityGroups = result.securityGroups();
        assertEquals(1, securityGroups.size());
        group = securityGroups.get(0);
        perms = group.ipPermissions();
        assertEquals(0, perms.size());
    }

}

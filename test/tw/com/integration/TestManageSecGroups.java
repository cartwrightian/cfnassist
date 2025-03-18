package tw.com.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestManageSecGroups {

    private static final String GROUP_NAME = "TestManageSecGroups";
    private CloudClient client;
    private static String groupId = "";
    private static Ec2Client ec2Client;

    @BeforeAll
    public static void onceBeforeAllTestsRuns() {
        ec2Client = EnvironmentSetupForTests.createEC2Client();
    }

    @BeforeEach
    public void beforeEachTestRuns() {
        client = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());

        deleteGroupIfPresent();

        CreateSecurityGroupRequest createRequest = CreateSecurityGroupRequest.builder().
                description("test group").
                vpcId(EnvironmentSetupForTests.MAIN_VPC_FOR_TEST).
                groupName(GROUP_NAME).build();
        CreateSecurityGroupResponse result = ec2Client.createSecurityGroup(createRequest);
        groupId = result.groupId();
    }

    private static void deleteGroupIfPresent() {
        DescribeSecurityGroupsRequest describeSecurityGroupsRequest = DescribeSecurityGroupsRequest.
                builder().
                build();

        DescribeSecurityGroupsResponse existing = ec2Client.describeSecurityGroups(describeSecurityGroupsRequest);

        Set<SecurityGroup> present = existing.securityGroups().stream().
                filter(securityGroup -> securityGroup.groupName().equals(GROUP_NAME)).collect(Collectors.toSet());

        present.forEach(securityGroup -> {
            DeleteSecurityGroupRequest deleteGroup = DeleteSecurityGroupRequest.builder().
                    groupId(securityGroup.groupId()).build();
            ec2Client.deleteSecurityGroup(deleteGroup);
        });

    }

    @AfterAll
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

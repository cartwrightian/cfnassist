package tw.com.providers;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.AwsRegionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import tw.com.exceptions.WrongNumberOfInstancesException;

import java.net.InetAddress;
import java.util.*;

import static java.lang.String.format;

public class CloudClient implements ProgressListener {
    private static final Logger logger = LoggerFactory.getLogger(CloudClient.class);

    private Ec2Client ec2Client;
    private AwsRegionProvider regionProvider;

    public CloudClient(Ec2Client ec2Client, AwsRegionProvider regionProvider) {
        this.ec2Client = ec2Client;
        this.regionProvider = regionProvider;
    }

    public Vpc describeVpc(String vpcId) {
        logger.info("Get VPC by ID " + vpcId);

        DescribeVpcsRequest describeVpcsRequest = DescribeVpcsRequest.
                builder().vpcIds(vpcId).
                build();

        DescribeVpcsResponse results = ec2Client.describeVpcs(describeVpcsRequest);
        return results.vpcs().get(0);
    }

    public List<Vpc> describeVpcs() {
        logger.info("Get All VPCs");

        DescribeVpcsResponse describeVpcsResults = ec2Client.describeVpcs();
        return describeVpcsResults.vpcs();
    }

    public void addTagsToResources(List<String> resources, List<Tag> tags) {
        CreateTagsRequest createTagsRequest = CreateTagsRequest.builder().resources(resources).tags(tags).build();
        ec2Client.createTags(createTagsRequest);
    }

    public void deleteTagsFromResources(List<String> resources, Tag tag) {
        DeleteTagsRequest deleteTagsRequest = DeleteTagsRequest.builder().resources(resources).tags(tag).build();
        ec2Client.deleteTags(deleteTagsRequest);
    }

    public Map<String, AvailabilityZone> getAvailabilityZones() {
        String regionName = regionProvider.getRegion();
        logger.info("Get AZ for region " + regionName);
        Filter filter = Filter.builder().name("region-name").values(regionName).build();
        DescribeAvailabilityZonesRequest request = DescribeAvailabilityZonesRequest.builder().
                filters(filter).build();


        DescribeAvailabilityZonesResponse result = ec2Client.describeAvailabilityZones(request);
        List<AvailabilityZone> zones = result.availabilityZones();
        logger.info(format("Found %s zones", zones.size()));

        Map<String, AvailabilityZone> zoneMap = new HashMap<>();
        zones.forEach(zone -> zoneMap.put(zone.zoneName().replace(zone.regionName(), ""), zone));
        return zoneMap;
    }

    public software.amazon.awssdk.services.ec2.model.Instance getInstanceById(String id) throws WrongNumberOfInstancesException {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(id).build();
        DescribeInstancesResponse result = ec2Client.describeInstances(request);
        List<Reservation> res = result.reservations();
        if (res.size() != 1) {
            throw new WrongNumberOfInstancesException(id, res.size());
        }
        List<software.amazon.awssdk.services.ec2.model.Instance> ins = res.get(0).instances();
        if (ins.size() != 1) {
            throw new WrongNumberOfInstancesException(id, ins.size());
        }
        return ins.get(0);
    }

    public List<Vpc> getVpcs() {
        DescribeVpcsResponse describeVpcsResults = ec2Client.describeVpcs();
        return describeVpcsResults.vpcs();
    }

    public List<Subnet> getAllSubnets() {
        DescribeSubnetsResponse describeResults = ec2Client.describeSubnets();
        return describeResults.subnets();
    }

    public List<SecurityGroup> getSecurityGroups() {
        DescribeSecurityGroupsResponse result = ec2Client.describeSecurityGroups();
        return result.securityGroups();
    }

    public List<NetworkAcl> getACLs() {
        DescribeNetworkAclsResponse result = ec2Client.describeNetworkAcls();
        return result.networkAcls();
    }

    public List<Instance> getInstances() {
        List<Instance> instances = new LinkedList<>();
        DescribeInstancesResponse result = ec2Client.describeInstances();
        List<Reservation> reservations = result.reservations();
        for (Reservation res : reservations) {
            instances.addAll(res.instances());
        }
        return instances;
    }

    public List<RouteTable> getRouteTables() {
        DescribeRouteTablesResponse result = ec2Client.describeRouteTables();
        return result.routeTables();
    }

    public List<Address> getEIPs() {
        DescribeAddressesResponse result = ec2Client.describeAddresses();
        return result.addresses();
    }

    public void addIpsToSecGroup(String secGroupId, Integer port, List<InetAddress> addresses) {
        logger.info(format("Add addresses %s for port %s to group %s", addresses, port.toString(), secGroupId));

        AuthorizeSecurityGroupIngressRequest request = AuthorizeSecurityGroupIngressRequest.builder().
                groupId(secGroupId).ipPermissions(createPermissions(port, addresses)).build();

        // TODO
        //request.setGeneralProgressListener(this);
        ec2Client.authorizeSecurityGroupIngress(request);
    }

    public void deleteIpFromSecGroup(String groupId, Integer port, List<InetAddress> addresses) {
        logger.info(format("Remove addresses %s for port %s on group %s", addresses, port.toString(), groupId));
        RevokeSecurityGroupIngressRequest request = RevokeSecurityGroupIngressRequest.builder().
                groupId(groupId).ipPermissions(createPermissions(port, addresses)).build();

        // TODO
        //request.setGeneralProgressListener(this);
        ec2Client.revokeSecurityGroupIngress(request);
    }

    private Collection<IpPermission> createPermissions(Integer port, List<InetAddress> addresses) {

        Collection<IpPermission> ipPermissions = new LinkedList<>();
        addresses.forEach(address ->{
            IpRange ipRange = IpRange.builder().cidrIp(format("%s/32", address.getHostAddress())).build();

            IpPermission permission = IpPermission.builder().
                    fromPort(port).toPort(port).ipProtocol("tcp").ipRanges(ipRange).build();
            ipPermissions.add(permission);
        });

        return ipPermissions;
    }

    @Override
    public void progressChanged(ProgressEvent progressEvent) {
        if (progressEvent.getEventType() == ProgressEventType.CLIENT_REQUEST_FAILED_EVENT) {
            logger.warn(progressEvent.toString());
        }
        logger.info(progressEvent.toString());
    }

    // unencrypted PEM encoded RSA private key
    public AWSPrivateKey createKeyPair(String keypairName) {
        logger.info("Create keypair with name " + keypairName);
        CreateKeyPairRequest request = CreateKeyPairRequest.builder().keyName(keypairName).build();
        CreateKeyPairResponse result = ec2Client.createKeyPair(request);

        logger.info(format("Created keypair %s with fingerprint %s", result.keyName(), result.keyFingerprint()));

        return new AWSPrivateKey(result.keyName(),result.keyMaterial());
    }

    public static class AWSPrivateKey {

        private final String keyName;
        private final String material;

        public AWSPrivateKey(String keyName, String material) {

            this.keyName = keyName;
            this.material = material;
        }

        public String getKeyName() {
            return keyName;
        }

        public String getMaterial() {
            return material;
        }
    }

}

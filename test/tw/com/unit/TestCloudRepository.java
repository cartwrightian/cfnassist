package tw.com.unit;

import org.junit.jupiter.api.Assertions;
import software.amazon.awssdk.services.ec2.model.*;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;
import tw.com.providers.SavesFile;
import tw.com.repository.CloudRepository;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

class TestCloudRepository extends EasyMockSupport {
	
	CloudRepository repository;
	private CloudClient cloudClient;
    private String home;

    @BeforeEach
	public void beforeEachTestRuns() {
		cloudClient = createStrictMock(CloudClient.class);
		repository = new CloudRepository(cloudClient);
        home = System.getenv("HOME");
    }
	
	@Test
    void shouldReturnSubnetsForGivenVPCId() {
		String vpcId = "vpcId";
		String subnetId = "subnetId";
		
		EasyMock.expect(cloudClient.getAllSubnets()).andReturn(createSubnets(vpcId, subnetId));
		
		replayAll();
		repository.getSubnetsForVpc(vpcId);
		List<Subnet> result = repository.getSubnetsForVpc(vpcId); // cached
		verifyAll();
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals(subnetId, result.get(0).subnetId());
	}

	@Test
    void shouldReturnAvailabilityZones() {
		AvailabilityZone zone = AvailabilityZone.builder().regionName("regionName").zoneName("regionaNameA").build();
		Map<String, AvailabilityZone> zones = new HashMap<>();
		zones.put("A", zone);
		EasyMock.expect(cloudClient.getAvailabilityZones()).andReturn(zones);

		replayAll();
		repository.getZones();
		Map<String, AvailabilityZone> result = repository.getZones(); // cached
		verifyAll();

		Assertions.assertEquals(zone, result.get("A"));
	}
	
	@Test
    void shouldGetSubnetById() {
		String vpcId = "vpcId";
		String subnetId = "subnetId";
		
		EasyMock.expect(cloudClient.getAllSubnets()).andReturn(createSubnets(vpcId, subnetId));
		
		replayAll();
		repository.getSubnetById(subnetId);
		Subnet result = repository.getSubnetById(subnetId); // cached
		verifyAll();

		Assertions.assertEquals(subnetId, result.subnetId());
	}
	
	@Test
    void getShouldBeAbleToFindEIPsForAVPC() throws CfnAssistException {
		String vpcId = "vpcId";	
		String matchingAddress = "42.41.40.39";
		List<Address> addresses = new LinkedList<>();
		List<Instance> instances = new LinkedList<>();

		Instance instanceA = Instance.builder().instanceId("ins123").vpcId(vpcId).build();
		Instance instanceB = Instance.builder().instanceId("ins456").vpcId("someOtherId").build();
		instances.add(instanceA);
		instances.add(instanceB);

		addresses.add(Address.builder().privateIpAddress(matchingAddress).instanceId(instanceA.instanceId()).build());
		addresses.add(Address.builder().privateIpAddress("10.9.8.7").instanceId(instanceB.instanceId()).build());
		
		EasyMock.expect(cloudClient.getEIPs()).andReturn(addresses);
		EasyMock.expect(cloudClient.getInstances()).andReturn(instances);
		
		replayAll();
		repository.getEIPForVPCId(vpcId);
		List<Address> result = repository.getEIPForVPCId(vpcId); //cached
		verifyAll();
		
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals(matchingAddress, result.get(0).privateIpAddress());
	}
	
	@Test
    void shouldBeAbleToGetGroupsByNameAndId() throws CfnAssistException  {
		String groupId = "groupId";
		String groupName = "groupName";
		
		List<SecurityGroup> groups = new LinkedList<>();
		groups.add(SecurityGroup.builder().groupId("xxxx1").groupName("abcgc").build());
		groups.add(SecurityGroup.builder().groupId(groupId).groupName(groupName).build());
		groups.add(SecurityGroup.builder().groupId("xxxx2").groupName("zzzhdh").build());
		
		EasyMock.expect(cloudClient.getSecurityGroups()).andReturn(groups);

		replayAll();
		SecurityGroup resultById = repository.getSecurityGroupById(groupId);
		SecurityGroup resultByName = repository.getSecurityGroupByName(groupName);
		repository.getSecurityGroupById(groupId); //cached
		verifyAll();
		
		Assertions.assertEquals(groupName, resultById.groupName());
		Assertions.assertEquals(groupId, resultByName.groupId());
	}
	
	@Test
    void shouldBeAbleToGetInstanceById() throws CfnAssistException {
		String instanceId = "instanceId1";
		String subnetId = "subnetId";
		List<Instance> instances = createInstances(instanceId, subnetId);
		
		EasyMock.expect(cloudClient.getInstances()).andReturn(instances);
		
		replayAll();
		Instance result = repository.getInstanceById(instanceId);
		repository.getInstanceById(instanceId); // cached
		verifyAll();
		Assertions.assertEquals(instanceId, result.instanceId());
	}
	
	@Test
    void shouldFindInstancesForASubnet() {
		String subnetId = "subnetId";
		String instanceId = "instanceId";
		String instanceIdB = "instanceId1";

		List<Instance> instances = createInstances(instanceId,subnetId);
		instances.add(Instance.builder().instanceId(instanceIdB).subnetId(subnetId).build());
	
		EasyMock.expect(cloudClient.getInstances()).andReturn(instances);
		
		replayAll();
		List<Instance> result = repository.getInstancesForSubnet(subnetId);
		repository.getInstancesForSubnet(subnetId); // cached
		verifyAll();
		Assertions.assertEquals(2, result.size());
	}
	
	@Test
    void shouldGetRouteTablesForVPC() {
		String vpcId = "vpcId";
		String tableId = "tableId";

		List<RouteTable> tables = new LinkedList<>();
		tables.add(RouteTable.builder().routeTableId("someId").vpcId("someVpcID").build());
		tables.add(RouteTable.builder().routeTableId(tableId).vpcId(vpcId).build());
		tables.add(RouteTable.builder().routeTableId("someOtherId").vpcId("someOtherVpcID").build());
	
		EasyMock.expect(cloudClient.getRouteTables()).andReturn(tables);
		
		replayAll();
		List<RouteTable> result = repository.getRouteTablesForVPC(vpcId);
		repository.getRouteTablesForVPC(vpcId); //cached
		verifyAll();
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals(tableId, result.get(0).routeTableId());
	}
	
	@Test
    void shouldGetACLSForVPC() {
		String vpcId = "vpcId";
		String aclId = "aclId";

		List<NetworkAcl> acls = new LinkedList<>();
		acls.add(NetworkAcl.builder().networkAclId("someId").vpcId("someVpcID").build());
		acls.add(NetworkAcl.builder().networkAclId(aclId).vpcId(vpcId).build());
		acls.add(NetworkAcl.builder().networkAclId("someOtherId").vpcId("someOtherVpcID").build());
	
		EasyMock.expect(cloudClient.getACLs()).andReturn(acls);
		
		replayAll();
		List<NetworkAcl> result = repository.getALCsForVPC(vpcId);
		repository.getALCsForVPC(vpcId); //cached
		verifyAll();
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals(aclId, result.get(0).networkAclId());
	}

	@Test
    void shouldAddIpsAndPortToASecurityGroup() throws UnknownHostException {
		String groupId = "groupId";
		Integer port = 8081;
		InetAddress addressA = Inet4Address.getByName("192.168.0.1");
        InetAddress addressB = Inet4Address.getByName("192.168.0.2");
        List<InetAddress> addresses = new LinkedList<>();
        addresses.add(addressA);
        addresses.add(addressB);

		cloudClient.addIpsToSecGroup(groupId , port, addresses);
		EasyMock.expectLastCall();

		replayAll();
		repository.updateAddIpsAndPortToSecGroup(groupId, addresses, port);
		verifyAll();
	}
	
	@Test
    void shouldRemoveIpAndPortFromASecurityGroup() throws UnknownHostException {
		String groupId = "groupId";
		Integer port = 8081;

        InetAddress addressA = Inet4Address.getByName("192.168.0.1");
        InetAddress addressB = Inet4Address.getByName("192.168.0.2");
        List<InetAddress> addresses = new LinkedList<>();
        addresses.add(addressA);
        addresses.add(addressB);

		cloudClient.deleteIpFromSecGroup(groupId , port, addresses);
		EasyMock.expectLastCall();
		
		replayAll();
		repository.updateRemoveIpsAndPortFromSecGroup(groupId, addresses, port);
		verifyAll();
	}
	
	@Test
    void shouldGetTagsForAnInstance() throws WrongNumberOfInstancesException {
		String instanceId = "someId";
		
		Tag tag = EnvironmentSetupForTests.createEc2Tag("theKey","theValue");
		Instance theInstance = Instance.builder().instanceId(instanceId).tags(tag).build();
		EasyMock.expect(cloudClient.getInstanceById(instanceId)).andReturn(theInstance);
		
		replayAll();
		List<Tag> results = repository.getTagsForInstance(instanceId);
		verifyAll();
		
		Assertions.assertEquals(1, results.size());
		Tag result = results.get(0);
		Assertions.assertEquals("theKey", result.key());
		Assertions.assertEquals("theValue", result.value());
	}

	@Test
    void shouldCreateKeyPairAndSaveToFile() throws CfnAssistException, IOException {
        SavesFile savesFile = createStrictMock(SavesFile.class);

        Path filename = Paths.get(format("%s/.ssh/keyName.pem", home));

        String material = "somePem";
		CloudClient.AWSPrivateKey key = new CloudClient.AWSPrivateKey("name", material);
		EasyMock.expect(cloudClient.createKeyPair("keyName")).andReturn(key);
        EasyMock.expect(savesFile.save(filename, material)).andReturn(true);
        savesFile.ownerOnlyPermisssion(filename);
        EasyMock.expectLastCall();

        replayAll();
        repository.createKeyPair("keyName", savesFile, filename);
        verifyAll();
    }

	@Test
    void shouldGetIPForAnEIPAllocationId() {
        List<Address> addresses = new LinkedList<>();
        addresses.add(Address.builder().allocationId("allocationId").publicIp("10.1.2.3").build());
        EasyMock.expect(cloudClient.getEIPs()).andReturn(addresses);

        replayAll();
        String result = repository.getIpFor("allocationId");
        verifyAll();

        Assertions.assertEquals("10.1.2.3", result);
    }

	private List<Subnet> createSubnets(String vpcId, String subnetId) {
		Subnet matchingSubnet = Subnet.builder().vpcId(vpcId).subnetId(subnetId).build();
		
		List<Subnet> subnets = new LinkedList<>();
		subnets.add(Subnet.builder().vpcId("anotherId").build());
		subnets.add(matchingSubnet);
		subnets.add(Subnet.builder().vpcId("anotherId").build());
		return subnets;
	}
	
	private List<Instance> createInstances(String instanceId, String subnetId) {
		List<Instance> instances = new LinkedList<>();
		instances.add(Instance.builder().instanceId("anotherID1").subnetId("subnetAAAAXXX").build());
		instances.add(Instance.builder().instanceId(instanceId).subnetId(subnetId).build());
		instances.add(Instance.builder().instanceId("anotherID2").subnetId("subnetBBBBYYY").build());
		return instances;
	}
}

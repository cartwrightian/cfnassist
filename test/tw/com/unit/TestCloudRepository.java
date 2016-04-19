package tw.com.unit;

import com.amazonaws.services.ec2.model.*;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;
import tw.com.providers.SavesFile;
import tw.com.repository.CloudRepository;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(EasyMockRunner.class)
public class TestCloudRepository extends EasyMockSupport {
	
	CloudRepository repository;
	private CloudClient cloudClient;
    private String home;

    @Before
	public void beforeEachTestRuns() {
		cloudClient = createStrictMock(CloudClient.class);
		repository = new CloudRepository(cloudClient);
        home = System.getenv("HOME");
    }
	
	@Test
	public void shouldReturnSubnetsForGivenVPCId() {
		String vpcId = "vpcId";
		String subnetId = "subnetId";
		
		EasyMock.expect(cloudClient.getAllSubnets()).andReturn(createSubnets(vpcId, subnetId));
		
		replayAll();
		repository.getSubnetsForVpc(vpcId);
		List<Subnet> result = repository.getSubnetsForVpc(vpcId); // cached
		verifyAll();
		assertEquals(1, result.size());
		assertEquals(subnetId, result.get(0).getSubnetId());
	}

	@Test
	public void shouldReturnAvailabilityZones() {
		AvailabilityZone zone = new AvailabilityZone().withRegionName("regionName").withZoneName("regionaNameA");
		Map<String, AvailabilityZone> zones = new HashMap<>();
		zones.put("A", zone);
		EasyMock.expect(cloudClient.getAvailabilityZones("regionName")).andReturn(zones);

		replayAll();
		repository.getZones("regionName");
		Map<String, AvailabilityZone> result = repository.getZones("regionName"); // cached
		verifyAll();

		assertEquals(zone, result.get("A"));
	}
	
	@Test
	public void shouldGetSubnetById() {
		String vpcId = "vpcId";
		String subnetId = "subnetId";
		
		EasyMock.expect(cloudClient.getAllSubnets()).andReturn(createSubnets(vpcId, subnetId));
		
		replayAll();
		repository.getSubnetById(subnetId);
		Subnet result = repository.getSubnetById(subnetId); // cached
		verifyAll();

		assertEquals(subnetId, result.getSubnetId());
	}
	
	@Test
	public void getShouldBeAbleToFindEIPsForAVPC() throws CfnAssistException {
		String vpcId = "vpcId";	
		String matchingAddress = "42.41.40.39";
		List<Address> addresses = new LinkedList<>();
		List<Instance> instances = new LinkedList<>();

		Instance instanceA = new Instance().withInstanceId("ins123").withVpcId(vpcId);
		Instance instanceB = new Instance().withInstanceId("ins456").withVpcId("someOtherId");
		instances.add(instanceA);
		instances.add(instanceB);

		addresses.add(new Address().withPrivateIpAddress(matchingAddress).withInstanceId(instanceA.getInstanceId()));
		addresses.add(new Address().withPrivateIpAddress("10.9.8.7").withInstanceId(instanceB.getInstanceId()));
		
		EasyMock.expect(cloudClient.getEIPs()).andReturn(addresses);
		EasyMock.expect(cloudClient.getInstances()).andReturn(instances);
		
		replayAll();
		repository.getEIPForVPCId(vpcId);
		List<Address> result = repository.getEIPForVPCId(vpcId); //cached
		verifyAll();
		
		assertEquals(1, result.size());
		assertEquals(matchingAddress, result.get(0).getPrivateIpAddress());
	}
	
	@Test
	public void shouldBeAbleToGetGroupsByNameAndId() throws CfnAssistException  {
		String groupId = "groupId";
		String groupName = "groupName";
		
		List<SecurityGroup> groups = new LinkedList<>();
		groups.add(new SecurityGroup().withGroupId("xxxx1").withGroupName("abcgc"));
		groups.add(new SecurityGroup().withGroupId(groupId).withGroupName(groupName));
		groups.add(new SecurityGroup().withGroupId("xxxx2").withGroupName("zzzhdh"));
		
		EasyMock.expect(cloudClient.getSecurityGroups()).andReturn(groups);

		replayAll();
		SecurityGroup resultById = repository.getSecurityGroupById(groupId);
		SecurityGroup resultByName = repository.getSecurityGroupByName(groupName);
		repository.getSecurityGroupById(groupId); //cached
		verifyAll();
		
		assertEquals(groupName, resultById.getGroupName());
		assertEquals(groupId, resultByName.getGroupId());
	}
	
	@Test
	public void shouldBeAbleToGetInstanceById() throws CfnAssistException {
		String instanceId = "instanceId1";
		String subnetId = "subnetId";
		List<Instance> instances = createInstances(instanceId, subnetId);
		
		EasyMock.expect(cloudClient.getInstances()).andReturn(instances);
		
		replayAll();
		Instance result = repository.getInstanceById(instanceId);
		repository.getInstanceById(instanceId); // cached
		verifyAll();
		assertEquals(instanceId, result.getInstanceId());
	}
	
	@Test
	public void shouldFindInstancesForASubnet() {
		String subnetId = "subnetId";
		String instanceId = "instanceId";
		String instanceIdB = "instanceId1";

		List<Instance> instances = createInstances(instanceId,subnetId);
		instances.add(new Instance().withInstanceId(instanceIdB).withSubnetId(subnetId));
	
		EasyMock.expect(cloudClient.getInstances()).andReturn(instances);
		
		replayAll();
		List<Instance> result = repository.getInstancesForSubnet(subnetId);
		repository.getInstancesForSubnet(subnetId); // cached
		verifyAll();
		assertEquals(2, result.size());
	}
	
	@Test
	public void shouldGetRouteTablesForVPC() {
		String vpcId = "vpcId";
		String tableId = "tableId";

		List<RouteTable> tables = new LinkedList<>();
		tables.add(new RouteTable().withRouteTableId("someId").withVpcId("someVpcID"));
		tables.add(new RouteTable().withRouteTableId(tableId).withVpcId(vpcId));
		tables.add(new RouteTable().withRouteTableId("someOtherId").withVpcId("someOtherVpcID"));
	
		EasyMock.expect(cloudClient.getRouteTables()).andReturn(tables);
		
		replayAll();
		List<RouteTable> result = repository.getRouteTablesForVPC(vpcId);
		repository.getRouteTablesForVPC(vpcId); //cached
		verifyAll();
		assertEquals(1,  result.size());
		assertEquals(tableId, result.get(0).getRouteTableId());
	}
	
	@Test
	public void shouldGetACLSForVPC() {
		String vpcId = "vpcId";
		String aclId = "aclId";

		List<NetworkAcl> acls = new LinkedList<>();
		acls.add(new NetworkAcl().withNetworkAclId("someId").withVpcId("someVpcID"));
		acls.add(new NetworkAcl().withNetworkAclId(aclId).withVpcId(vpcId));
		acls.add(new NetworkAcl().withNetworkAclId("someOtherId").withVpcId("someOtherVpcID"));
	
		EasyMock.expect(cloudClient.getACLs()).andReturn(acls);
		
		replayAll();
		List<NetworkAcl> result = repository.getALCsForVPC(vpcId);
		repository.getALCsForVPC(vpcId); //cached
		verifyAll();
		assertEquals(1,  result.size());
		assertEquals(aclId, result.get(0).getNetworkAclId());
	}
	
	@Test
	public void shouldAddIpAndPortToASecurityGroup() throws UnknownHostException {
		String groupId = "groupId";
		Integer port = 8081;
		InetAddress adddress = Inet4Address.getByName("192.168.0.1");

		
		cloudClient.addIpToSecGroup(groupId , port, adddress);
		EasyMock.expectLastCall();
		
		replayAll();
		repository.updateAddIpAndPortToSecGroup(groupId, adddress, port);
		verifyAll();

	}
	
	@Test
	public void shouldRemoveIpAndPortFromASecurityGroup() throws UnknownHostException {
		String groupId = "groupId";
		Integer port = 8081;
		InetAddress adddress = Inet4Address.getByName("192.168.0.2");

		
		cloudClient.deleteIpFromSecGroup(groupId , port, adddress);
		EasyMock.expectLastCall();
		
		replayAll();
		repository.updateRemoveIpAndPortFromSecGroup(groupId, adddress, port);
		verifyAll();
	}
	
	@Test
	public void shouldGetTagsForAnInstance() throws WrongNumberOfInstancesException {
		String instanceId = "someId";
		
		Tag tag = new Tag().withKey("theKey").withValue("theValue");
		Instance theInstance = new Instance().withInstanceId(instanceId).withTags(tag);
		EasyMock.expect(cloudClient.getInstanceById(instanceId)).andReturn(theInstance);
		
		replayAll();
		List<Tag> results = repository.getTagsForInstance(instanceId);
		verifyAll();
		
		assertEquals(1, results.size());
		Tag result = results.get(0);
		assertEquals("theKey", result.getKey());
		assertEquals("theValue", result.getValue());
	}

	@Test
	public void shouldCreateKeyPairAndSaveToFile() throws CfnAssistException {
        SavesFile savesFile = createStrictMock(SavesFile.class);

        String filename = format("%s/.ssh/keyName.pem", home);

        String material = "somePem";
        EasyMock.expect(cloudClient.createKeyPair("keyName")).
                andReturn(new KeyPair().withKeyFingerprint("fingerprint").withKeyMaterial(material));
        EasyMock.expect(savesFile.exists(filename)).andReturn(false);
        EasyMock.expect(savesFile.save(filename, material)).andReturn(true);

        replayAll();
        repository.createKeyPair("keyName", savesFile);
        verifyAll();
    }

    @Test
    public void shouldNotCreateKeyIfFileExists() {
        SavesFile savesFile = createStrictMock(SavesFile.class);

        String filename = format("%s/.ssh/keyName.pem", home);

        EasyMock.expect(savesFile.exists(filename)).andReturn(true);

        replayAll();
        try {
            repository.createKeyPair("keyName", savesFile);
            fail("should have thrown");
        } catch (CfnAssistException e) {
            // noop - expected
        }
        verifyAll();
    }
	
	private List<Subnet> createSubnets(String vpcId, String subnetId) {
		Subnet matchingSubnet = new Subnet().withVpcId(vpcId).withSubnetId(subnetId);
		
		List<Subnet> subnets = new LinkedList<>();
		subnets.add(new Subnet().withVpcId("anotherId"));	
		subnets.add(matchingSubnet);
		subnets.add(new Subnet().withVpcId("anotherId"));
		return subnets;
	}
	
	private List<Instance> createInstances(String instanceId, String subnetId) {
		List<Instance> instances = new LinkedList<>();
		instances.add(new Instance().withInstanceId("anotherID1").withSubnetId("subnetAAAAXXX"));
		instances.add(new Instance().withInstanceId(instanceId).withSubnetId(subnetId));
		instances.add(new Instance().withInstanceId("anotherID2").withSubnetId("subnetBBBBYYY"));
		return instances;
	}
}

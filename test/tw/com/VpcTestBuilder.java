package tw.com;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.easymock.EasyMock;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclAssociation;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.PortRange;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RouteTableAssociation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSecurityGroupMembership;
import com.amazonaws.services.rds.model.DBSubnetGroup;

public class VpcTestBuilder {
	private List<Subnet> subnets;
	private List<RouteTable> routeTables;
	private List<Address> eips;
	private List<LoadBalancerDescription> loadBalancers;
	private List<DBInstance> databases;
	private List<Instance> instances;
	private String vpcId;
	private List<NetworkAcl> acls;
	private List<SecurityGroup> securityGroups;
	//
	private Vpc vpc;
	private Subnet insSubnet;
	private String subnetId;
	private Subnet dbSubnet;
	private Instance instance;
	private RouteTable routeTable;
	private RouteTableAssociation association;
	private Address eip;
	private LoadBalancerDescription elb;
	private DBInstance dbInstance;
	private NetworkAclAssociation aclAssoc;
	private PortRange portRange;
	private NetworkAclEntry outboundEntry;
	private NetworkAclEntry inboundEntry;
	private NetworkAcl acl;
	private IpPermission ipPermsInbound;
	private IpPermission ipPermsOutbound;
	private SecurityGroup instSecurityGroup;
	private SecurityGroup dbSecurityGroup;
	private IpPermission ipDbPermsInbound;
	private IpPermission ipDbPermsOutbound;
	private IpPermission ipElbPermsInbound;
	private IpPermission ipElbPermsOutbound;
	private SecurityGroup elbSecurityGroup;
	
	public VpcTestBuilder(String vpcId) {
		this.vpcId = vpcId;
		subnets = new LinkedList<Subnet>();
		instances = new LinkedList<Instance>();
		routeTables = new LinkedList<RouteTable>();
		eips = new LinkedList<Address>();
		loadBalancers = new LinkedList<LoadBalancerDescription>();
		databases = new LinkedList<DBInstance>();
		acls = new LinkedList<>();
		securityGroups = new LinkedList<>();
		//
		createVPC();
	}
	
	private void createVPC() {
		vpc = new Vpc().withVpcId(vpcId);
		insSubnet = new Subnet().
				withSubnetId("subnetIdA").
				withCidrBlock("cidrBlockA");
		dbSubnet = new Subnet().
				withSubnetId("subnetIdDB").
				withCidrBlock("cidrBlockDB");
		subnetId = insSubnet.getSubnetId();
		instance = new Instance().
				withInstanceId("instanceId").
				withTags(CreateNameTag("instanceName")).
				withPrivateIpAddress("privateIp");
		String instanceId = instance.getInstanceId();
		association = new RouteTableAssociation().
				withRouteTableAssociationId("assocId").
				withSubnetId(subnetId);
		routeTable = new RouteTable().
				withRouteTableId("routeTableId").
				withAssociations(association);
		eip = new Address().
				withAllocationId("eipAllocId").
				withInstanceId(instanceId).
				withPublicIp("publicIP");	
		
		ipElbPermsInbound = new IpPermission().withFromPort(20).withToPort(29).withIpProtocol("tcp").withIpRanges("ipRanges");
		ipElbPermsOutbound = new IpPermission().withFromPort(200).withToPort(300).withIpProtocol("tcp").withIpRanges("ipRanges");
		elbSecurityGroup = new SecurityGroup().
				withGroupId("secElbGroupId").
				withGroupName("secElbGroupName").
				withIpPermissions(ipElbPermsInbound).
				withIpPermissionsEgress(ipElbPermsOutbound);
		
		elb = new LoadBalancerDescription().
				withLoadBalancerName("loadBalancerName").
				withDNSName("lbDNSName").
				withSecurityGroups(elbSecurityGroup.getGroupId());
		dbInstance = new DBInstance().
				withDBInstanceIdentifier("dbInstanceId").
				withDBName("dbName");
		aclAssoc = new NetworkAclAssociation().
				withSubnetId(subnetId);
		portRange = new PortRange().
				withFrom(1024).
				withTo(2048);
		outboundEntry = new NetworkAclEntry().
				withEgress(true).
				withCidrBlock("cidrBlockOut").
				withPortRange(portRange).
				withRuleAction("allow").
				withProtocol("6").
				withRuleNumber(42);
		inboundEntry = new NetworkAclEntry().
				withEgress(false).
				withCidrBlock("cidrBlockIn").
				withPortRange(portRange).
				withRuleAction("allow").
				withProtocol("6").
				withRuleNumber(43);
		acl = new NetworkAcl().withAssociations(aclAssoc).
				withEntries(outboundEntry, inboundEntry).
				withNetworkAclId("aclId");
		ipPermsInbound = new IpPermission().withFromPort(80).withToPort(89).withIpProtocol("tcp").withIpRanges("ipRanges");
		ipPermsOutbound = new IpPermission().withFromPort(600).withToPort(700).withIpProtocol("tcp").withIpRanges("ipRanges");
		instSecurityGroup = new SecurityGroup().
				withGroupId("secGroupId").
				withGroupName("secGroupName").
				withIpPermissions(ipPermsInbound).
				withIpPermissionsEgress(ipPermsOutbound);
		
		ipDbPermsInbound = new IpPermission().withFromPort(90).withToPort(99).withIpProtocol("tcp").withIpRanges("ipRanges");
		ipDbPermsOutbound = new IpPermission().withFromPort(700).withToPort(800).withIpProtocol("tcp").withIpRanges("ipRanges");
		dbSecurityGroup = new SecurityGroup().
				withGroupId("secDbGroupId").
				withGroupName("secDbGroupName").
				withIpPermissions(ipDbPermsInbound).
				withIpPermissionsEgress(ipDbPermsOutbound);
		AddItemsToVpc();
	}
	
	private void AddItemsToVpc() {
		add(insSubnet);
		add(dbSubnet);
		add(instance);	
		add(routeTable);
		add(eip);
		addAndAssociate(elb);
		addAndAssociate(dbInstance);
		add(acl);
		addAndAssociateWithInstances(instSecurityGroup);
		addAndAssociateWithDBs(dbSecurityGroup);	
	}
	
	public Vpc getVpc() {
		return vpc;
	}

	private void addAndAssociateWithDBs(SecurityGroup securityGroup) {
		for(DBInstance db  : databases) {
			DBSecurityGroupMembership groupMembership = new DBSecurityGroupMembership().withDBSecurityGroupName(securityGroup.getGroupName());
			db.withDBSecurityGroups(groupMembership);
		}	
	}

	private void add(Subnet subnet) {
		subnets.add(subnet);
	}

	private void add(Instance instance) {
		instances.add(instance);
	}

	private void add(RouteTable routeTable) {
		routeTables.add(routeTable);
	}
	
	private void add(Address address) {
		eips.add(address);
	}
	
	private void addAndAssociate(LoadBalancerDescription elb) {
		loadBalancers.add(elb);	
		// instances
		Collection<com.amazonaws.services.elasticloadbalancing.model.Instance> list = new LinkedList<com.amazonaws.services.elasticloadbalancing.model.Instance>();
		for(Instance i : instances) {
			list.add(new com.amazonaws.services.elasticloadbalancing.model.Instance().withInstanceId(i.getInstanceId()));
		}
		elb.setInstances(list);
		// subnets
		List<String> subnetIds = new LinkedList<String>();
		for(Subnet s : subnets) {
			subnetIds.add(s.getSubnetId());
		}
		elb.setSubnets(subnetIds);
	}
	
	private void addAndAssociate(DBInstance dbInstance) {
		databases.add(dbInstance);
		
		List<com.amazonaws.services.rds.model.Subnet> rdsSubnets = new LinkedList<>();
		rdsSubnets.add(new com.amazonaws.services.rds.model.Subnet().withSubnetIdentifier(dbSubnet.getSubnetId()));
		DBSubnetGroup dBSubnetGroup = new DBSubnetGroup();
		dBSubnetGroup.setSubnets(rdsSubnets);
		dbInstance.withDBSubnetGroup(dBSubnetGroup);
	}
	
	private void add(NetworkAcl acl) {
		acls.add(acl);	
	}
	
	private void addAndAssociateWithInstances(SecurityGroup securityGroup) {
		securityGroups.add(securityGroup);
		GroupIdentifier groupId = new GroupIdentifier().withGroupId(securityGroup.getGroupId()).withGroupName(securityGroup.getGroupName());
		for(Instance i : instances) {
			i.withSecurityGroups(groupId);
		}
	}

	public Vpc setFacadeVisitExpections(AmazonVPCFacade awsFacade) throws CfnAssistException {
		EasyMock.expect(awsFacade.getSubnetFors(vpcId)).andStubReturn(subnets);
		EasyMock.expect(awsFacade.getInstancesFor(subnetId)).andStubReturn(instances);
		EasyMock.expect(awsFacade.getInstancesFor(dbSubnet.getSubnetId())).andStubReturn(new LinkedList<Instance>());

		EasyMock.expect(awsFacade.getRouteTablesFor(vpcId)).andReturn(routeTables);
		EasyMock.expect(awsFacade.getEIPFor(vpcId)).andReturn(eips);
		EasyMock.expect(awsFacade.getLBsFor(vpcId)).andReturn(loadBalancers);
		EasyMock.expect(awsFacade.getSecurityGroupDetailsById(elbSecurityGroup.getGroupId())).andReturn(elbSecurityGroup);
		EasyMock.expect(awsFacade.getRDSFor(vpcId)).andReturn(databases);
		EasyMock.expect(awsFacade.getSecurityGroupDetailsByName(dbSecurityGroup.getGroupName())).andReturn(dbSecurityGroup);
		EasyMock.expect(awsFacade.getACLs(vpcId)).andReturn(acls);
		SecurityGroup instanceSecurityGroup = securityGroups.get(0); // TODO more than one
		EasyMock.expect(awsFacade.getSecurityGroupDetailsById(instanceSecurityGroup.getGroupId())).andReturn(instanceSecurityGroup);
		return vpc;	
	}
	
	public void setGetVpcsExpectations(AmazonVPCFacade awsFacade) {
		List<Vpc> vpcs = new LinkedList<>();
		vpcs.add(vpc);
		EasyMock.expect(awsFacade.getVpcs()).andReturn(vpcs);	
	}
	
	public static Tag CreateNameTag(String name) {
		return new Tag().withKey("Name").withValue(name);
	}

	public Subnet getSubnet() {
		return insSubnet;
	}

	public String getSubnetId() {
		return subnetId;
	}

	public Instance getInstance() {
		return instance;
	}

	public RouteTable getRouteTable() {
		return routeTable;
	}

	public Address getEip() {
		return eip;
	}

	public LoadBalancerDescription getElb() {
		return elb;
	}

	public DBInstance getDbInstance() {
		return dbInstance;
	}

	public NetworkAclEntry getOutboundEntry() {
		return outboundEntry;
	}

	public NetworkAclEntry getInboundEntry() {
		return inboundEntry;
	}

	public NetworkAcl getAcl() {
		return acl;
	}

	public IpPermission getInstanceIpPermsInbound() {
		return ipPermsInbound;
	}

	public IpPermission getInstanceIpPermsOutbound() {
		return ipPermsOutbound;
	}

	public SecurityGroup getInstanceSecurityGroup() {
		return instSecurityGroup;
	}

	public SecurityGroup getDBSecurityGroup() {
		return dbSecurityGroup;
	}

	public String getDbSubnetId() {
		return dbSubnet.getSubnetId();
	}

	public IpPermission getDbIpPermsInbound() {
		return ipDbPermsInbound;
	}

	public IpPermission getDbIpPermsOutbound() {
		return ipDbPermsOutbound;
	}

	public Subnet getDbSubnet() {
		return dbSubnet;
	}

	public SecurityGroup getElbSecurityGroup() {
		return elbSecurityGroup;
	}

	public IpPermission getElbIpPermsInbound() {
		return ipElbPermsInbound;
	}

	public IpPermission getElbIpPermsOutbound() {
		return ipElbPermsOutbound;
	}

}

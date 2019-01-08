package tw.com;

import software.amazon.awssdk.services.ec2.model.*;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSecurityGroupMembership;
import com.amazonaws.services.rds.model.DBSubnetGroup;
import org.easymock.EasyMock;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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
	private List<Route> routes;

	//
	private Vpc vpc;
	private Subnet insSubnet;
	private String subnetId;
	private Subnet dbSubnet;
	private Instance instance;
	private RouteTable routeTable;
	private Address eip;
	private LoadBalancerDescription elb;
	private DBInstance dbInstance;
	private NetworkAclEntry outboundAclEntry;
	private NetworkAclEntry inboundAclEntry;
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

	IpRange range = IpRange.builder().cidrIp("ipRanges").build();

	public VpcTestBuilder(String vpcId) {
		this.vpcId = vpcId;
		subnets = new LinkedList<>();
		instances = new LinkedList<>();
		routeTables = new LinkedList<>();
		eips = new LinkedList<>();
		loadBalancers = new LinkedList<>();
		databases = new LinkedList<>();
		acls = new LinkedList<>();
		securityGroups = new LinkedList<>();
		routes = new LinkedList<>();
		//
		createVPC();
	}
	
	private void createVPC() {
		vpc = Vpc.builder().vpcId(vpcId).build();
		insSubnet = Subnet.builder().
				subnetId("subnetIdA").
				cidrBlock("10.1.0.0/16").build();
		dbSubnet = Subnet.builder().
				subnetId("subnetIdDB").
				cidrBlock("10.2.0.0/16").build();
		subnetId = insSubnet.subnetId();
		GroupIdentifier secGroupId = GroupIdentifier.builder().groupId("secGroupId").build();
		instance = Instance.builder().
				instanceId("instanceId").
				tags(CreateNameTag("instanceName")).
				privateIpAddress("privateIp").securityGroups(secGroupId).
				build();
		RouteTableAssociation routeTableAssociationA = RouteTableAssociation.builder().
				routeTableAssociationId("assocId").
				subnetId(subnetId).build();
		RouteTableAssociation routeTableAssociationB = RouteTableAssociation.builder().
				routeTableAssociationId("assocId").
				subnetId(dbSubnet.subnetId()).build();
		add(Route.builder().destinationCidrBlock("10.1.0.11/32").gatewayId("igwId").state(RouteState.ACTIVE).build());
		add(Route.builder().destinationCidrBlock("10.1.0.12/32").instanceId("instanceId").state(RouteState.ACTIVE).build());
		add(Route.builder().destinationCidrBlock("10.1.0.13/32").state(RouteState.BLACKHOLE).build());
		routeTable = RouteTable.builder().
				routeTableId("routeTableId").
				associations(routeTableAssociationA, routeTableAssociationB).
				routes(routes).build();
		eip = Address.builder().
				allocationId("eipAllocId").
				instanceId(instance.instanceId()).
				publicIp("publicIP").build();

		ipElbPermsInbound = IpPermission.builder().fromPort(20).toPort(29).ipProtocol("tcp").ipRanges(range).build();
		ipElbPermsOutbound = IpPermission.builder().fromPort(200).toPort(300).ipProtocol("tcp").ipRanges(range).build();
		elbSecurityGroup = SecurityGroup.builder().
				groupId("secElbGroupId").
				groupName("secElbGroupName").
				ipPermissions(ipElbPermsInbound).
				ipPermissionsEgress(ipElbPermsOutbound).build();

		LoadBalancerDescription.Builder elbBuilder = LoadBalancerDescription.builder().
				loadBalancerName("loadBalancerName").
				dnsName("lbDNSName").
				securityGroups(elbSecurityGroup.groupId());
		dbInstance = new DBInstance().
				withDBInstanceIdentifier("dbInstanceId").
				withDBName("dbName");
		NetworkAclAssociation aclAssoc = NetworkAclAssociation.builder().
				subnetId(subnetId).build();
		PortRange portRange = PortRange.builder().
				from(1024).
				to(2048).build();
		outboundAclEntry = NetworkAclEntry.builder().
				egress(true).
				cidrBlock("cidrBlockOut").
				portRange(portRange).
				ruleAction("allow").
				protocol("6").
				ruleNumber(42).build();
		inboundAclEntry = NetworkAclEntry.builder().
				egress(false).
				cidrBlock("cidrBlockIn").
				portRange(portRange).
				ruleAction("allow").
				protocol("6").
				ruleNumber(43).build();
		acl = NetworkAcl.builder().associations(aclAssoc).
				entries(outboundAclEntry, inboundAclEntry).
				networkAclId("aclId").build();
		ipPermsInbound = IpPermission.builder().fromPort(80).toPort(89).ipProtocol("tcp").ipRanges(range).build();
		ipPermsOutbound = IpPermission.builder().fromPort(600).toPort(700).ipProtocol("tcp").ipRanges(range).build();
		instSecurityGroup = SecurityGroup.builder().
				groupId("secGroupId").
				groupName("secGroupName").
				ipPermissions(ipPermsInbound).
				ipPermissionsEgress(ipPermsOutbound).build();
		securityGroups.add(instSecurityGroup);

		ipDbPermsInbound = IpPermission.builder().fromPort(90).toPort(99).ipProtocol("tcp").ipRanges(range).build();
		ipDbPermsOutbound = IpPermission.builder().fromPort(700).toPort(800).ipProtocol("tcp").ipRanges(range).build();
		dbSecurityGroup = SecurityGroup.builder().
				groupId("secDbGroupId").
				groupName("secDbGroupName").
				ipPermissions(ipDbPermsInbound).
				ipPermissionsEgress(ipDbPermsOutbound).build();
		AddItemsToVpc(elbBuilder);
	}
	
	private void AddItemsToVpc(LoadBalancerDescription.Builder elbBuilder) {
		add(insSubnet);
		add(dbSubnet);
		add(instance);
		add(routeTable);
		add(eip);
		elb = addAndAssociate(elbBuilder);
		addAndAssociate(dbInstance);
		add(acl);
		addAndAssociateWithDBs(dbSecurityGroup);
	}

	private void add(Route route) {
		routes.add(route);		
	}

	public Vpc getVpc() {
		return vpc;
	}

	private void addAndAssociateWithDBs(SecurityGroup securityGroup) {
		for(DBInstance db  : databases) {
			DBSecurityGroupMembership groupMembership = new DBSecurityGroupMembership().withDBSecurityGroupName(securityGroup.groupName());
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
	
	private LoadBalancerDescription addAndAssociate(LoadBalancerDescription.Builder builder) {
		// instances
		Collection<software.amazon.awssdk.services.elasticloadbalancing.model.Instance> list = new LinkedList<>();
		for(Instance i : instances) {
			list.add(software.amazon.awssdk.services.elasticloadbalancing.model.Instance.builder().
					instanceId(i.instanceId()).build());
		}
		builder.instances(list);
		// subnets
		List<String> subnetIds = new LinkedList<>();
		for(Subnet s : subnets) {
			subnetIds.add(s.subnetId());
		}
		builder.subnets(subnetIds);

		LoadBalancerDescription balancerDescription = builder.build();
		loadBalancers.add(balancerDescription);
		return balancerDescription;
	}
	
	private void addAndAssociate(DBInstance dbInstance) {
		databases.add(dbInstance);
		
		List<com.amazonaws.services.rds.model.Subnet> rdsSubnets = new LinkedList<>();
		rdsSubnets.add(new com.amazonaws.services.rds.model.Subnet().withSubnetIdentifier(dbSubnet.subnetId()));
		DBSubnetGroup dBSubnetGroup = new DBSubnetGroup();
		dBSubnetGroup.setSubnets(rdsSubnets);
		dbInstance.withDBSubnetGroup(dBSubnetGroup);
	}
	
	private void add(NetworkAcl acl) {
		acls.add(acl);	
	}

	public Vpc setFacadeVisitExpections(AmazonVPCFacade awsFacade) throws CfnAssistException {
		EasyMock.expect(awsFacade.getSubnetFors(vpcId)).andStubReturn(subnets);
		EasyMock.expect(awsFacade.getInstancesFor(subnetId)).andStubReturn(instances);
		EasyMock.expect(awsFacade.getInstancesFor(dbSubnet.subnetId())).andStubReturn(new LinkedList<>());

		EasyMock.expect(awsFacade.getRouteTablesFor(vpcId)).andReturn(routeTables);
		EasyMock.expect(awsFacade.getEIPFor(vpcId)).andReturn(eips);
		EasyMock.expect(awsFacade.getLBsFor(vpcId)).andReturn(loadBalancers);
		EasyMock.expect(awsFacade.getSecurityGroupDetailsById(elbSecurityGroup.groupId())).andReturn(elbSecurityGroup);
		EasyMock.expect(awsFacade.getRDSFor(vpcId)).andReturn(databases);
		EasyMock.expect(awsFacade.getSecurityGroupDetailsByName(dbSecurityGroup.groupName())).andReturn(dbSecurityGroup);
		EasyMock.expect(awsFacade.getACLs(vpcId)).andReturn(acls);
		SecurityGroup instanceSecurityGroup = securityGroups.get(0); // TODO more than one
		EasyMock.expect(awsFacade.getSecurityGroupDetailsById(instanceSecurityGroup.groupId())).andReturn(instanceSecurityGroup);
		return vpc;	
	}
	
	public void setGetVpcsExpectations(AmazonVPCFacade awsFacade) {
		List<Vpc> vpcs = new LinkedList<>();
		vpcs.add(vpc);
		EasyMock.expect(awsFacade.getVpcs()).andReturn(vpcs);	
	}
	
	public static Tag CreateNameTag(String name) {
		return Tag.builder().key("Name").value(name).build();
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
		return outboundAclEntry;
	}

	public NetworkAclEntry getInboundEntry() {
		return inboundAclEntry;
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
		return dbSubnet.subnetId();
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

	public Route getRouteA() {
		return routes.get(0);
	}
	
	public Route getRouteB() {
		return routes.get(1);
	}
	
	public Route getRouteC() {
		return routes.get(2);
	}

}

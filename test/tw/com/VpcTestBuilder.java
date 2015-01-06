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
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSubnetGroup;

public class VpcTestBuilder {
	private Vpc vpc;
	private List<Subnet> subnets;
	private List<RouteTable> routeTables;
	private List<Address> eips;
	private List<LoadBalancerDescription> loadBalancers;
	private List<DBInstance> databases;
	private List<Instance> instances;
	private String vpcId;
	private List<NetworkAcl> acls;
	private List<SecurityGroup> securityGroups;
	
	public VpcTestBuilder(String vpcId) {
		this.vpcId = vpcId;
		this.vpc = new Vpc().withVpcId(vpcId);
		subnets = new LinkedList<Subnet>();
		instances = new LinkedList<Instance>();
		routeTables = new LinkedList<RouteTable>();
		eips = new LinkedList<Address>();
		loadBalancers = new LinkedList<LoadBalancerDescription>();
		databases = new LinkedList<DBInstance>();
		acls = new LinkedList<>();
		securityGroups = new LinkedList<>();
	}

	public void add(Subnet subnet) {
		subnets.add(subnet);
	}

	public void add(Instance instance) {
		instances.add(instance);
	}

	public void add(RouteTable routeTable) {
		routeTables.add(routeTable);
	}
	

	public void add(Address address) {
		eips.add(address);
	}
	

	public void addAndAssociate(LoadBalancerDescription elb) {
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
	
	public void addAndAssociate(DBInstance dbInstance) {
		databases.add(dbInstance);
		
		List<com.amazonaws.services.rds.model.Subnet> rdsSubnets = new LinkedList<com.amazonaws.services.rds.model.Subnet>();
		for(Subnet s : subnets) {
			rdsSubnets.add(new com.amazonaws.services.rds.model.Subnet().withSubnetIdentifier(s.getSubnetId()));
		}
		DBSubnetGroup dBSubnetGroup = new DBSubnetGroup();
		dBSubnetGroup.setSubnets(rdsSubnets);
		dbInstance.withDBSubnetGroup(dBSubnetGroup);
	}
	

	public void add(NetworkAcl acl) {
		acls.add(acl);	
	}
	
	public void addAndAssociate(SecurityGroup securityGroup) {
		securityGroups.add(securityGroup);
		GroupIdentifier groupId = new GroupIdentifier().withGroupId(securityGroup.getGroupId()).withGroupName(securityGroup.getGroupName());
		for(Instance i : instances) {
			i.withSecurityGroups(groupId);
		}
	}

	public Vpc setFacadeExpectations(AmazonVPCFacade awsFacade, String subnetId) throws CfnAssistException {
		EasyMock.expect(awsFacade.getSubnetFors(vpcId)).andStubReturn(subnets);
		EasyMock.expect(awsFacade.getInstancesFor(subnetId)).andStubReturn(instances);
		EasyMock.expect(awsFacade.getRouteTablesFor(vpcId)).andReturn(routeTables);
		EasyMock.expect(awsFacade.getEIPFor(vpcId)).andReturn(eips);
		EasyMock.expect(awsFacade.getLBsFor(vpcId)).andReturn(loadBalancers);
		EasyMock.expect(awsFacade.getRDSFor(vpcId)).andReturn(databases);
		EasyMock.expect(awsFacade.getACLs(vpcId)).andReturn(acls);
		EasyMock.expect(awsFacade.getLBsFor(vpcId)).andReturn(loadBalancers);
		SecurityGroup securityGroup = securityGroups.get(0); // TODO more than one
		EasyMock.expect(awsFacade.getSecurityGroupDetailsById(securityGroup.getGroupId())).andReturn(securityGroup);
		return vpc;	
	}
	
	public static Tag CreateNameTag(String routeTableName) {
		return new Tag().withKey("Name").withValue(routeTableName);
	}

}

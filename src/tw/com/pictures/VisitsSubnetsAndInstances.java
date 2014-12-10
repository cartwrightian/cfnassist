package tw.com.pictures;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import tw.com.exceptions.CfnAssistException;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

public class VisitsSubnetsAndInstances {
	public static final int SUBNET_TITLE_FONT_SIZE = 12;
	private AmazonVPCFacade facade;

	private LinkedList<String> subnetsWhereNotSeenRoutingTableYet;
	
	private String vpcId;
	private VisitsSecGroupsAndACLs securityVisitor;

	public VisitsSubnetsAndInstances(AmazonVPCFacade facade, VisitsSecGroupsAndACLs securityVisitor, String vpcId) {
		this.vpcId = vpcId;
		this.facade = facade;
		this.securityVisitor = securityVisitor;
	}
	
	public void visit() throws CfnAssistException {
		subnetsWhereNotSeenRoutingTableYet = new LinkedList<String>();
		
		for (Subnet subnet : facade.getSubnetFors(vpcId)) {
			walkSubnetAndInstances(subnet);
			subnetsWhereNotSeenRoutingTableYet.add(subnet.getSubnetId());
		}		
	}

	private void walkSubnetAndInstances(Subnet subnet) throws CfnAssistException {	
		Map<Instance, String> instanceLabelPairs = new HashMap<Instance, String>(); // instance -> label
		securityVisitor.walkInstances(instanceLabelPairs, subnet);		
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.getSubnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.getSubnetId(), subnet.getCidrBlock());
	}


	public void addLoadBalancer(String subnetId, LoadBalancerDescription description) throws CfnAssistException {
		securityVisitor.walkLoadBalancer(subnetId, description);
	}

}

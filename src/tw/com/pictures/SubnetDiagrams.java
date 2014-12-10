package tw.com.pictures;

import java.util.HashMap;
import java.util.Map;

import tw.com.exceptions.CfnAssistException;

import com.amazonaws.services.ec2.model.Subnet;

public class SubnetDiagrams {

	private Diagram parentDiagram;
	private Map<String, ChildDiagram> childDiagrams = new HashMap<String, ChildDiagram>(); // subnetId -> diagram

	public ChildDiagram addDiagramFor(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String subnetLabel = formSubnetLabel(subnet, subnetName);
		String subnetId = subnet.getSubnetId();	
		
		ChildDiagram childDiagram = parentDiagram.createDiagramCluster(subnetId, subnetLabel);		
		childDiagrams.put(subnetId,childDiagram);
		
		return childDiagram;
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.getSubnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.getSubnetId(), subnet.getCidrBlock());
	}

}

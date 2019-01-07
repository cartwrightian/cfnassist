package tw.com.pictures;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.ec2.model.Subnet;
import tw.com.exceptions.CfnAssistException;

public class SubnetDiagrams {

	private Diagram parentDiagram;
	private Map<String, ChildDiagram> childDiagrams = new HashMap<String, ChildDiagram>(); // subnetId -> diagram

	public ChildDiagram addDiagramFor(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.tags());
		String subnetLabel = formSubnetLabel(subnet, subnetName);
		String subnetId = subnet.subnetId();
		
		ChildDiagram childDiagram = parentDiagram.createSubDiagram(subnetId, subnetLabel);		
		childDiagrams.put(subnetId,childDiagram);
		
		return childDiagram;
	}
	
	public static String formSubnetLabel(Subnet subnet, String tagName) {
		String name = subnet.subnetId();
		if (!tagName.isEmpty()) {
			name = tagName;
		} 
		return String.format("%s [%s]\n(%s)", name, subnet.subnetId(), subnet.cidrBlock());
	}

}

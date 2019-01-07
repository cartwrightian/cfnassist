package tw.com.pictures;

import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Vpc;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.GraphFacade;

public class DiagramFactory {

	public VPCDiagramBuilder createVPCDiagramBuilder(Vpc vpc) {
		GraphFacade networkDiagram = new GraphFacade();
		GraphFacade securityDiagram = new GraphFacade();
		return new VPCDiagramBuilder(vpc, networkDiagram, securityDiagram);
	}

	public SubnetDiagramBuilder createSubnetDiagramBuilder(VPCDiagramBuilder parentBuilder, Subnet subnet) throws CfnAssistException {	
		NetworkChildDiagram diagram = parentBuilder.createNetworkDiagramForSubnet(subnet);
		SecurityChildDiagram securityDiagram = parentBuilder.createSecurityDiagramForSubnet(subnet);
		return new SubnetDiagramBuilder(diagram, securityDiagram, subnet);
	}

}

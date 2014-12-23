package tw.com.pictures;

import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.GraphFacade;

public class DiagramFactory {

	public VPCDiagramBuilder createVPCDiagramBuilder(Vpc vpc) {
		GraphFacade diagram = new GraphFacade();
		return new VPCDiagramBuilder(vpc, diagram);
	}

	public SubnetDiagramBuilder createSubnetDiagramBuilder(VPCDiagramBuilder parent, Subnet subnet) throws CfnAssistException {	
		ChildDiagram diagram = parent.createDiagramForSubnet(subnet);
		return new SubnetDiagramBuilder(diagram, parent, subnet);
	}

}

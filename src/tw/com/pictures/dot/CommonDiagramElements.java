package tw.com.pictures.dot;

import tw.com.exceptions.CfnAssistException;

public class CommonDiagramElements implements CommonElements {
	
	NodesAndEdges graph;
	
	public CommonDiagramElements(NodesAndEdges graph) {
		this.graph = graph;
	}
	
	@Override
	public void addSecurityGroup(String id, String label) throws CfnAssistException {
		graph.addNode(id).withLabel(label).withShape(Shape.Box);		
	}

	@Override
	public void addPortRange(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withLabel(label);		
	}

	@Override
	public void connectWithLabel(String uniqueIdA, String uniqueIdB, String label) {
		graph.addEdge(uniqueIdA, uniqueIdB).withLabel(label);	
	}
}

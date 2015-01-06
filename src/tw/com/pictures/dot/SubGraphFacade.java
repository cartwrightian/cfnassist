package tw.com.pictures.dot;

import javax.management.InvalidApplicationException;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;

public class SubGraphFacade implements ChildDiagram {

	private SubGraph graph;

	public SubGraphFacade(SubGraph subGraph) {
		this.graph = subGraph ;
	}

	@Override
	public void addInstance(String instanceId, String label) throws CfnAssistException, InvalidApplicationException {
		graph.addNode(instanceId).withShape(Shape.Box).withLabel(label);
	}

	@Override
	public void render(Recorder recorder) {
		graph.render(recorder);	
	}

	@Override
	public void addRouteTable(String routeTableId, String label) throws CfnAssistException {
		graph.addNode(routeTableId).addLabel(label);		
	}

	@Override
	public String getId() {
		return graph.getId();
	}

	@Override
	public void addSecurityGroup(String id, String label) throws CfnAssistException, InvalidApplicationException {
		graph.addNode(id).withLabel(label).withShape(Shape.Box);		
	}

	@Override
	public void addPortRange(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withLabel(label);
		
	}

	@Override
	public void connectWithLabel(String uniqueAId, String uniqueBId, String label) {
		graph.addEdge(uniqueAId, uniqueBId).withLabel(label);	
	}

}

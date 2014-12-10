package tw.com.pictures.dot;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;

public class SubGraphFacade implements ChildDiagram {

	private SubGraph graph;

	public SubGraphFacade(SubGraph subGraph) {
		this.graph = subGraph ;
	}

	@Override
	public void addInstance(String instanceId, String label) throws CfnAssistException {
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

}

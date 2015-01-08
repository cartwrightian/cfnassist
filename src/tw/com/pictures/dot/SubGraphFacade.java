package tw.com.pictures.dot;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;

public class SubGraphFacade implements ChildDiagram {

	private SubGraph subGraph;
	private CommonElements commonElements;

	public SubGraphFacade(SubGraph subGraph) {
		this.subGraph = subGraph ;
		commonElements = new CommonDiagramElements(subGraph);
	}

	@Override
	public void addInstance(String instanceId, String label) throws CfnAssistException {
		subGraph.addNode(instanceId).withShape(Shape.Box).withLabel(label);
	}

	@Override
	public void render(Recorder recorder) {
		subGraph.render(recorder);	
	}

	@Override
	public void addRouteTable(String routeTableId, String label) throws CfnAssistException {
		subGraph.addNode(routeTableId).addLabel(label);		
	}

	@Override
	public String getId() {
		return subGraph.getId();
	}

	@Override
	public void addPortRange(String uniqueId, String label) throws CfnAssistException {
		commonElements.addPortRange(uniqueId, label);
	}

	@Override
	public void addSecurityGroup(String uniqueId, String label) throws CfnAssistException {
		commonElements.addSecurityGroup(uniqueId, label);
	}

	@Override
	public void connectWithLabel(String uniqueAId, String uniqueBId, String label) {
		commonElements.connectWithLabel(uniqueAId, uniqueBId, label);	
	}

}

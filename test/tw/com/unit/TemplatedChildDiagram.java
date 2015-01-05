package tw.com.unit;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;
import tw.com.pictures.dot.Recorder;

public class TemplatedChildDiagram<T extends ChildDiagram> implements ChildDiagram {
	
	private T contained;
	
	public ChildDiagram getContained() {
		return contained;
	}
	
	public TemplatedChildDiagram(T contained) {
		this.contained = contained;
	}

	@Override
	public void addInstance(String instanceId, String label)
			throws CfnAssistException {
		contained.addInstance(instanceId, label);
	}

	@Override
	public void render(Recorder recorder) {
		contained.render(recorder);
	}

	@Override
	public void addRouteTable(String routeTableId, String label)
			throws CfnAssistException {
		contained.addRouteTable(routeTableId, label);
	}

	@Override
	public String getId() {
		return contained.getId();
	}

	@Override
	public void addSecurityGroup(String id, String label) throws CfnAssistException {
		contained.addSecurityGroup(id, label);	
	}

}

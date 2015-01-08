package tw.com.pictures;


import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.CommonElements;
import tw.com.pictures.dot.Recorder;

public interface ChildDiagram extends CommonElements {

	void addInstance(String uniqueId, String label) throws CfnAssistException;

	void render(Recorder recorder);

	void addRouteTable(String uniqueId, String label) throws CfnAssistException;
	
	public void connectWithLabel(String uniqueAId, String uniqueBId, String label);
	
	String getId();

}

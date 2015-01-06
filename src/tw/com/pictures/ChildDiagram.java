package tw.com.pictures;

import javax.management.InvalidApplicationException;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;

public interface ChildDiagram {

	void addInstance(String uniqueId, String label) throws CfnAssistException, InvalidApplicationException;

	void render(Recorder recorder);

	void addRouteTable(String uniqueId, String label) throws CfnAssistException;

	void addSecurityGroup(String uniqueId, String label) throws CfnAssistException, InvalidApplicationException;
	
	void addPortRange(String uniqueId, String label) throws CfnAssistException;
	
	public void connectWithLabel(String uniqueAId, String uniqueBId, String label);
	
	String getId();

}

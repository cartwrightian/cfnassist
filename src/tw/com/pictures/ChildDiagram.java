package tw.com.pictures;

import javax.management.InvalidApplicationException;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;

public interface ChildDiagram {

	void addInstance(String instanceId, String label) throws CfnAssistException, InvalidApplicationException;

	void render(Recorder recorder);

	void addRouteTable(String routeTableId, String label) throws CfnAssistException;

	void addSecurityGroup(String string, String label) throws CfnAssistException, InvalidApplicationException;
	
	String getId();

}

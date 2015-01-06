package tw.com.pictures;

import javax.management.InvalidApplicationException;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;

public interface Diagram {

	ChildDiagram createSubDiagram(String uniqueId, String label) throws CfnAssistException;

	void addTitle(String title);

	void render(Recorder recorder);

	void addRouteTable(String uniqueId, String label) throws CfnAssistException;

	void addConnectionBetween(String uniqueIdA, String uniqueIdB);

	void addPublicIPAddress(String unqiueId, String label) throws CfnAssistException, InvalidApplicationException;

	void addLoadBalancer(String unqiueId, String label) throws CfnAssistException, InvalidApplicationException;

	void associateWithSubDiagram(String unqiueId, String clusterId, HasDiagramId childDiagram);
	
	void addConnectionFromSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws InvalidApplicationException;
	
	void addConnectionToSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws InvalidApplicationException;
	
	void addBlockedConnectionFromSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws InvalidApplicationException;
	
	void addBlockedConnectionToSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws InvalidApplicationException;

	void addDBInstance(String rdsId, String label) throws CfnAssistException, InvalidApplicationException;

	void addACL(String uniqueId, String label) throws CfnAssistException, InvalidApplicationException;

	void addCidr(String uniqueId, String label) throws CfnAssistException, InvalidApplicationException;

	void associate(String uniqueIdA, String uniqueIdB);

}

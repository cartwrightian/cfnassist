package tw.com.pictures;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.CommonElements;
import tw.com.pictures.dot.Recorder;

public interface Diagram extends CommonElements {

	ChildDiagram createSubDiagram(String uniqueId, String label) throws CfnAssistException;

	void addTitle(String title);

	void render(Recorder recorder);

	void addRouteTable(String uniqueId, String label) throws CfnAssistException;

	void addConnectionBetween(String uniqueIdA, String uniqueIdB);

	void addPublicIPAddress(String unqiueId, String label) throws CfnAssistException;

	void addLoadBalancer(String unqiueId, String label) throws CfnAssistException;

	void associateWithSubDiagram(String unqiueId, String clusterId, HasDiagramId childDiagram);
	
	void addConnectionFromSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws CfnAssistException;
	
	void addConnectionToSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws CfnAssistException;
	
	void addBlockedConnectionFromSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws CfnAssistException;
	
	void addBlockedConnectionToSubDiagram(String start, String end, HasDiagramId childDigram, String label) throws CfnAssistException;

	void addDBInstance(String rdsId, String label) throws CfnAssistException;

	void addACL(String uniqueId, String label) throws CfnAssistException;

	void addCidr(String uniqueId, String label) throws CfnAssistException;

	void associate(String uniqueIdA, String uniqueIdB);

}

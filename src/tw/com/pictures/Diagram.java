package tw.com.pictures;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;

public interface Diagram {

	ChildDiagram createDiagramCluster(String uniqueId, String label) throws CfnAssistException;

	void addTitle(String title);

	void render(Recorder recorder);

	void addRouteTable(String uniqueId, String label) throws CfnAssistException;

	void addConnectionBetween(String uniqueIdA, String uniqueIdB);

	void addEIP(String unqiueId, String label) throws CfnAssistException;

	void addELB(String unqiueId, String label) throws CfnAssistException;

	void associateWithCluster(String unqiueId, String clusterId, SubnetDiagramBuilder subnetDiagramBuilder);
	
	void addConnectionFromCluster(String unqiueId, String subnetId, SubnetDiagramBuilder subnetDiagramBuilder, String label);

	void addDBInstance(String rdsId, String label) throws CfnAssistException;

}

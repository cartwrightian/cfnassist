package tw.com.pictures.dot;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;
import tw.com.pictures.Diagram;
import tw.com.pictures.SubnetDiagramBuilder;

public class GraphFacade implements Diagram {
	public static final int SUBNET_TITLE_FONT_SIZE = 12;

	Graph graph;
	
	public GraphFacade() {
		graph = new Graph();
	}
	
	@Override
	public ChildDiagram createDiagramCluster(String uniqueId, String label) throws CfnAssistException {
		SubGraph cluster = graph.createDiagramCluster(uniqueId, label, SUBNET_TITLE_FONT_SIZE);
		cluster.addNode(uniqueId).makeInvisible();
		ChildDiagram child = new SubGraphFacade(cluster);
		return child;
	}

	@Override
	public void addTitle(String title) {
		graph.addTitle(title);	
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
	public void addConnectionBetween(String uniqueIdA, String uniqueIdB) {
		graph.addEdge(uniqueIdA, uniqueIdB);
	}

	@Override
	public void addEIP(String address, String label) throws CfnAssistException {
		graph.addNode(address).withShape(Shape.Diamond).withLabel(label);	
	}

	@Override
	public void addELB(String dnsName, String label) throws CfnAssistException {
		graph.addNode(dnsName).withShape(Shape.Octogon).withLabel(label);	
	}
	

	@Override
	public void addConnectionFromCluster(String uniqueId, String subnetId,
			SubnetDiagramBuilder subnetDiagramBuilder, String label) {
		graph.addEdge(subnetId, uniqueId).beginsAt(subnetDiagramBuilder.getId()).withLabel(label);		
	}

	@Override
	public void associateWithCluster(String uniqueId, String subnetId, SubnetDiagramBuilder subnetDiagramBuilder) {
		graph.addEdge(uniqueId, subnetId).withDot().endsAt(subnetDiagramBuilder.getId());		
	}

	@Override
	public void addDBInstance(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withShape(Shape.Octogon).withLabel(label);	
	}


}

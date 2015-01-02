package tw.com.pictures.dot;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;
import tw.com.pictures.Diagram;
import tw.com.pictures.HasDiagramId;

public class GraphFacade implements Diagram {
	public static final int SUBNET_TITLE_FONT_SIZE = 12;

	Graph graph;
	
	public GraphFacade() {
		graph = new Graph();
	}
	
	@Override
	public ChildDiagram createSubDiagram(String uniqueId, String label) throws CfnAssistException {
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
	public void addPublicIPAddress(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withShape(Shape.Diamond).withLabel(label);	
	}

	@Override
	public void addLoadBalancer(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withShape(Shape.Octogon).withLabel(label);	
	}
	

	@Override
	public void addConnectionFromSubDiagram(String target, String start,
			HasDiagramId childDigram, String edgeLabel) {
		graph.addEdge(start, target).beginsAt(childDigram.getIdAsString()).withLabel(edgeLabel);		
	}

	@Override
	public void associateWithSubDiagram(String begin, String end, HasDiagramId childDiagram) {
		graph.addEdge(begin, end).withDot().endsAt(childDiagram.getIdAsString());		
	}

	@Override
	public void addDBInstance(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withShape(Shape.Octogon).withLabel(label);	
	}

	@Override
	public void addACL(String uniqueId, String label) throws CfnAssistException {
		graph.addNode(uniqueId).withLabel(label);		
	}

	@Override
	public void addPortRange(String uniqueId, int i, int j) throws CfnAssistException {
		String label = "notset";
		if (i==0 && j==0) {
			label = "any";
		} else if (i==j) {
			label = String.format("%03d", i);
		} else {
			label = String.format("%03d-%03d", i,j);
		}
		graph.addNode(uniqueId).withLabel(label);
	}

	@Override
	public void addCidr(String cidrBlock) throws CfnAssistException {
		graph.addNode(cidrBlock).withShape(Shape.Diamond);	
	}
}

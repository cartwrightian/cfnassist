package tw.com.pictures.dot;

import java.util.LinkedList;
import java.util.List;

import tw.com.exceptions.CfnAssistException;

public class NodesAndEdges extends HasAttributes {

	protected List<Node> nodes = new LinkedList<Node>();
	protected List<Edge> edges = new LinkedList<Edge>();

	public NodesAndEdges() {
		super();
	}

	public Node addNode(String id) throws CfnAssistException {
		if (id==null) {
			throw new CfnAssistException("name cannot be null");
		}
		Node node = new Node(id);
		nodes.add(node);
		return node;
	}
	
	public void addCompound() {
		super.addCompound();
	}
	
	public Edge addEdge(String begin, String end) {
		Edge edge = new Edge(begin, end);
		edges.add(edge);
		return edge;
	}

	public Edge addEdgeIgnoreDup(String begin, String end) {
		Edge edge = new Edge(begin, end);
		if (edges.contains(edge)) {
			int index = edges.indexOf(edge);
			return edges.get(index);
		}
		edges.add(edge);
		return edge;
	}
	
	protected void renderNodesAndEdges(Recorder recorder, boolean isGraph) {
		writeAttributes(recorder, isGraph);
		for(Node node : nodes) {
			node.write(recorder);	
		}
		for(Edge edge : edges) {
			edge.write(recorder);
		}
	}

}
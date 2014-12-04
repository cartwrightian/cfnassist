package tw.com.pictures.dot;

import java.util.LinkedList;
import java.util.List;

public class Graph extends NodesAndEdges implements Renders {
	private static final int DIAGRAM_TITLE_FONT_SIZE = 36;

	private List<SubGraph> subs = new LinkedList<SubGraph>();
	
	public Graph() {
		addCompound();
	}

	public SubGraph createSubGraph(String id) {
		SubGraph subGraph = new SubGraph(id);
		subs.add(subGraph);
		return subGraph;
	}

	public SubGraph createDiagramCluster(String id, String label, int fontSize) {
		SubGraph subDiagram = this.createSubGraph("cluster_"+id);	
		subDiagram.addLabel(label);
		subDiagram.fontSize(fontSize);
		return subDiagram;
	}
	
	@Override
	public void render(Recorder recorder) {
		recorder.writeline("digraph G { ");
		renderNodesAndEdges(recorder, true);
		for(SubGraph sub : subs) {
			sub.render(recorder);
		}
		recorder.write("}");
	}

	public void addTitle(String title) {
		addLabel(title);
		fontSize(DIAGRAM_TITLE_FONT_SIZE);	
	}
}

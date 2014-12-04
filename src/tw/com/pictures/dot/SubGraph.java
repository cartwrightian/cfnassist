package tw.com.pictures.dot;


public class SubGraph extends NodesAndEdges implements Renders {

	private String id;
	public SubGraph(String id) {
		this.id = id;
	}
	
	public String getId() {
		return id;
	}

	@Override
	public void render(Recorder recorder) {
		recorder.writeline(String.format("subgraph \"%s\" {",id));
		renderNodesAndEdges(recorder, true);
		recorder.writeline("}");	
	}

	

}

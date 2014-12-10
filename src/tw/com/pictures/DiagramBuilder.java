package tw.com.pictures;


import java.io.IOException;
import java.util.LinkedList;

import tw.com.pictures.dot.Recorder;


public class DiagramBuilder {
	
	private LinkedList<VPCDiagramBuilder> vpcDiagrams;

	public DiagramBuilder() {
		vpcDiagrams = new LinkedList<VPCDiagramBuilder>();
	}

	public void add(VPCDiagramBuilder diagram) {
		vpcDiagrams.add(diagram);
	}

	public void render(Recorder recorder) throws IOException {
		for(VPCDiagramBuilder diagram : vpcDiagrams) {
			diagram.render(recorder);
		}
	}

}

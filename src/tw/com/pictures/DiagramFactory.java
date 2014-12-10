package tw.com.pictures;

import tw.com.pictures.dot.GraphFacade;

public class DiagramFactory {

	public Diagram createDiagram() {
		return new GraphFacade();
	}

}

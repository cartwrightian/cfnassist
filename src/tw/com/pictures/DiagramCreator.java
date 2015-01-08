package tw.com.pictures;

import java.io.IOException;
import java.util.List;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import com.amazonaws.services.ec2.model.Vpc;

public class DiagramCreator {
	
	private AmazonVPCFacade facade;

	public DiagramCreator(AmazonVPCFacade facade) {
		this.facade = facade;	
	}

	public void createDiagrams(Recorder recorder) throws IOException, CfnAssistException {
			
		List<Vpc> vpcs = facade.getVpcs();
				
		DiagramBuilder diagrams = new DiagramBuilder();
		DiagramFactory diagramFactory = new DiagramFactory();
		VPCVisitor visitor = new VPCVisitor(diagrams, facade, diagramFactory);
		for(Vpc vpc : vpcs) {
			visitor.visit(vpc);
		}
		diagrams.render(recorder);
	}


}

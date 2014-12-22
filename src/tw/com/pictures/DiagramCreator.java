package tw.com.pictures;

import java.io.IOException;
import java.util.List;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.RDSClient;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;

import com.amazonaws.services.ec2.model.Vpc;

public class DiagramCreator {
	
	private CloudRepository cloudRepository;
	private ELBRepository elbRepository;
	private RDSClient rdsClient;
	
	public DiagramCreator(RDSClient rdsClient, CloudRepository cloudClient, ELBRepository elbRepository) {
		this.rdsClient = rdsClient;
		this.cloudRepository = cloudClient;
		this.elbRepository = elbRepository;
	}

	public void createDiagrams(Recorder recorder) throws IOException, CfnAssistException {
		
		AmazonVPCFacade facade = new AmazonVPCFacade(cloudRepository, elbRepository, rdsClient);
		
		List<Vpc> vpcs = facade.getVpcs();
		
		legacyCreateDiagram(recorder, facade, vpcs);
		
		DiagramBuilder diagrams = new DiagramBuilder();
		DiagramFactory diagramFactory = new DiagramFactory();
		VPCVisitor visitor = new VPCVisitor(diagrams, facade, diagramFactory);
		for(Vpc vpc : vpcs) {
			visitor.visit(vpc);
		}
		diagrams.render(recorder);
	}

	private void legacyCreateDiagram(Recorder recorder, AmazonVPCFacade facade,
			List<Vpc> vpcs) throws CfnAssistException, IOException {
		for(Vpc vpc : vpcs) {
			DescribesVPC describesVPC = new DescribesVPC(vpc, facade, recorder);
			describesVPC.walk();
			describesVPC.recordToFile();
		}
	}
}

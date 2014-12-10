package tw.com.pictures;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Graph;
import tw.com.pictures.dot.Recorder;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSubnetGroup;

public class DescribesVPC {
	private static final Logger logger = LoggerFactory.getLogger(DescribesVPC.class);
	
	private Vpc vpc;
	private Graph securityDiagram;
	private AmazonVPCFacade repository;
	private Recorder recorder;

	public DescribesVPC(Vpc vpc, AmazonVPCFacade repository, Recorder recorder) {
		this.vpc = vpc;
		this.repository = repository;
		this.recorder = recorder;

		this.securityDiagram = new Graph();		
	}

	public void walk() throws CfnAssistException   {	
		String vpcId = vpc.getVpcId();
		logger.info("Create diagrams for VPC ID:" +vpcId);

		addDiagramTitle(vpcId, securityDiagram);
		
		VisitsSecGroupsAndACLs securityVisitor = new VisitsSecGroupsAndACLs(repository, vpcId, securityDiagram);
		VisitsSubnetsAndInstances subnetsAndInstances = new VisitsSubnetsAndInstances(repository, securityVisitor, vpcId);	
		
		subnetsAndInstances.visit();
		
		for (DBInstance dbInstance : repository.getRDSFor(vpcId)) {
			walkDB(dbInstance, subnetsAndInstances, securityVisitor);
		}
	}

	private void addDiagramTitle(String vpcId, Graph diagram) {
		String title = vpcId;
		String name = AmazonVPCFacade.getNameFromTags(vpc.getTags());
		if (!name.isEmpty()) {
			title = title + String.format(" (%s)", name);
		}
		diagram.addTitle(title);
	}
	
	public void recordToFile() throws IOException {
		recordDiagramToFile("old_diagram_security_", recorder, securityDiagram);	
	}

	private void recordDiagramToFile(String prefix, Recorder recorder, Graph diagram) throws IOException {
		recorder.beginFor(vpc, prefix);
		diagram.render(recorder);
		recorder.end();
	}
	
	private void walkDB(DBInstance dbInstance, VisitsSubnetsAndInstances subnetsAndInstances, 
			VisitsSecGroupsAndACLs securityVisitor) throws CfnAssistException {
		
		DBSubnetGroup dbSubnetGroup = dbInstance.getDBSubnetGroup();

		if (dbSubnetGroup!=null) {
			for(com.amazonaws.services.rds.model.Subnet subnet : dbSubnetGroup.getSubnets()) {
				//subnetsAndInstances.addRDS(rdsId, subnet);
				securityVisitor.walkDBSecGroups(dbInstance, subnet);
			}
		}
		
	}
	
}

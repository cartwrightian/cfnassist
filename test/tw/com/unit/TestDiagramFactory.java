package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.ChildDiagram;
import tw.com.pictures.DiagramFactory;
import tw.com.pictures.VPCDiagramBuilder;

import com.amazonaws.services.ec2.model.Subnet;

@RunWith(EasyMockRunner.class)
public class TestDiagramFactory extends EasyMockSupport {
	
	private DiagramFactory factory;
	private VPCDiagramBuilder parentDiagramBuilder;
	private ChildDiagram childDiagram;
	
	@Before
	public void beforeEachTestRuns() {
		parentDiagramBuilder = createStrictMock(VPCDiagramBuilder.class);
		childDiagram = createStrictMock(ChildDiagram.class);
		
		factory = new DiagramFactory();
	}

	@Test
	public void shouldAddSubnetDiagrm() throws CfnAssistException {
		
		Subnet subnet = new Subnet().withSubnetId("subnetId").withCidrBlock("cidrBlock");
		EasyMock.expect(parentDiagramBuilder.createDiagramForSubnet(subnet)).andReturn(childDiagram);
		
		replayAll();
		factory.createSubnetDiagramBuilder(parentDiagramBuilder, subnet);
		verifyAll();		
	}

}

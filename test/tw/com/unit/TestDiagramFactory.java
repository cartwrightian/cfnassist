package tw.com.unit;

import static org.junit.Assert.*;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.*;

import com.amazonaws.services.ec2.model.Subnet;

@RunWith(EasyMockRunner.class)
public class TestDiagramFactory extends EasyMockSupport {
	
	private DiagramFactory factory;
	private VPCDiagramBuilder parentDiagramBuilder;
	private NetworkChildDiagram childNetworkDiagram;
	private tw.com.pictures.SecurityChildDiagram childSecurityDiagram;
	
	@Before
	public void beforeEachTestRuns() {
		parentDiagramBuilder = createStrictMock(VPCDiagramBuilder.class);
		childNetworkDiagram = createStrictMock(NetworkChildDiagram.class);
		childSecurityDiagram = createStrictMock(tw.com.pictures.SecurityChildDiagram.class);
		
		factory = new DiagramFactory();
	}

	@Test
	public void shouldAddSubnetDiagrm() throws CfnAssistException {
		
		Subnet subnet = new Subnet().withSubnetId("subnetId").withCidrBlock("cidrBlock");
		EasyMock.expect(parentDiagramBuilder.createNetworkDiagramForSubnet(subnet)).andReturn(childNetworkDiagram);
		EasyMock.expect(parentDiagramBuilder.createSecurityDiagramForSubnet(subnet)).andReturn(childSecurityDiagram);
		
		replayAll();
		SubnetDiagramBuilder result = factory.createSubnetDiagramBuilder(parentDiagramBuilder, subnet);
		verifyAll();	
		
		assertSame(childNetworkDiagram, result.getNetworkDiagram());
	}

}

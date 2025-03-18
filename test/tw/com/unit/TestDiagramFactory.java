package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.*;

import software.amazon.awssdk.services.ec2.model.Subnet;

class TestDiagramFactory extends EasyMockSupport {
	
	private DiagramFactory factory;
	private VPCDiagramBuilder parentDiagramBuilder;
	private NetworkChildDiagram childNetworkDiagram;
	private tw.com.pictures.SecurityChildDiagram childSecurityDiagram;
	
	@BeforeEach
	public void beforeEachTestRuns() {
		parentDiagramBuilder = createStrictMock(VPCDiagramBuilder.class);
		childNetworkDiagram = createStrictMock(NetworkChildDiagram.class);
		childSecurityDiagram = createStrictMock(tw.com.pictures.SecurityChildDiagram.class);
		
		factory = new DiagramFactory();
	}

	@Test
    void shouldAddSubnetDiagrm() throws CfnAssistException {
		
		Subnet subnet = Subnet.builder().subnetId("subnetId").cidrBlock("cidrBlock").build();
		EasyMock.expect(parentDiagramBuilder.createNetworkDiagramForSubnet(subnet)).andReturn(childNetworkDiagram);
		EasyMock.expect(parentDiagramBuilder.createSecurityDiagramForSubnet(subnet)).andReturn(childSecurityDiagram);
		
		replayAll();
		SubnetDiagramBuilder result = factory.createSubnetDiagramBuilder(parentDiagramBuilder, subnet);
		verifyAll();	
		
		Assertions.assertSame(childNetworkDiagram, result.getNetworkDiagram());
	}

}

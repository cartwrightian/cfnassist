package tw.com.unit;

import java.io.IOException;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.Recorder;

@RunWith(EasyMockRunner.class)
public class TestDiagramCreator extends EasyMockSupport {
	
	private AmazonVPCFacade awsFacade;
	private Recorder recorder;

	@Before
	public void beforeEachTestRuns() {
		awsFacade = createStrictMock(AmazonVPCFacade.class);
		recorder = createMock(Recorder.class);
	}
		
	@Test
	public void invokeDiagramCreation() throws IOException, CfnAssistException {
				
		VpcTestBuilder vpcTestBuilder = new VpcTestBuilder("vpcId");
		vpcTestBuilder.setGetVpcsExpectations(awsFacade);
		vpcTestBuilder.setFacadeVisitExpections(awsFacade);
		
		recorder.beginFor(vpcTestBuilder.getVpc(), "network_diagram");
		EasyMock.expectLastCall();
		recorder.writeline(EasyMock.anyString());
		EasyMock.expectLastCall().atLeastOnce();
		recorder.write(EasyMock.anyString());
		EasyMock.expectLastCall().atLeastOnce();
		recorder.end();
		EasyMock.expectLastCall();
		recorder.beginFor(vpcTestBuilder.getVpc(), "security_diagram");
		EasyMock.expectLastCall();
		recorder.end();
		EasyMock.expectLastCall();

		DiagramCreator creator = new DiagramCreator(awsFacade);

		replayAll();
		creator.createDiagrams(recorder);
		verifyAll();
	}

}

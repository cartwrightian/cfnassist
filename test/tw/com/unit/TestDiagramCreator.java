package tw.com.unit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.AmazonVPCFacade;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;

class TestDiagramCreator extends EasyMockSupport {
	
	private AmazonVPCFacade awsFacade;
	private Recorder recorder;
	private VpcTestBuilder vpcTestBuilder;

	@BeforeEach
	public void beforeEachTestRuns() throws CfnAssistException {
		awsFacade = createStrictMock(AmazonVPCFacade.class);
		recorder = createMock(Recorder.class);
		
		vpcTestBuilder = new VpcTestBuilder("vpcId");
		vpcTestBuilder.setGetVpcsExpectations(awsFacade);
		vpcTestBuilder.setFacadeVisitExpections(awsFacade);
	}
		
	@Test
    void invokeDiagramCreationWithMockedRecorder() throws IOException, CfnAssistException {
		
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

	@Test
    void invokeDiagramCreationWithRealRecorder() throws IOException, CfnAssistException {
		
		Recorder realRecorder = new FileRecorder(Paths.get("."));
		DiagramCreator creator = new DiagramCreator(awsFacade);

		replayAll();
		creator.createDiagrams(realRecorder);
		verifyAll();
		
		assert(new File("./network_diagramvpcId.dot").exists());
		assert(new File("./security_diagramvpcId.dot").exists());
		
	}

}

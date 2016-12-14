package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import org.apache.commons.cli.MissingArgumentException;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.*;
import tw.com.commandline.CommandExecutor;
import tw.com.commandline.Main;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.ArtifactUploader;
import tw.com.providers.ProvidesCurrentIp;
import tw.com.providers.SavesFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

@RunWith(EasyMockRunner.class)
public class TestCommandLineActionsLegacy extends EasyMockSupport {

	private AwsFacade facade;
	private FacadeFactory factory;

	private ProjectAndEnv projectAndEnv;
	Collection<Parameter> params;
	
	String comment = "theComment";

	@Before
	public void beforeEachTestRuns() {
		factory = createMock(FacadeFactory.class);
		facade = createMock(AwsFacade.class);

		params = new LinkedList<>();
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

		projectAndEnv.setComment(comment);
	}

	@Test
	public void shouldRollbackStacksFromDir() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();

		ArrayList<String> stacks = new ArrayList<>();
		EasyMock.expect(facade.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv)).andReturn(stacks);
			
		validate(CLIArgBuilder.rollbackFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, ""));
	}
	
	@Test
	public void testShouldStepBackOneChange() throws MissingArgumentException, CfnAssistException, InterruptedException {
		setFactoryExpectations();
		
		List<String> deleted = new LinkedList<>();
		EasyMock.expect(facade.stepbackLastChangeFromFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv)).andReturn(deleted);
		
		validate(CLIArgBuilder.stepback(FilesForTesting.ORDERED_SCRIPTS_FOLDER, ""));
	}

	@Test
	public void shouldRollbackStacksFromDirWithSNS() throws MissingArgumentException, CfnAssistException, InterruptedException, IOException {
		setFactoryExpectations();
		factory.setSNSMonitoring();

		ArrayList<String> stacks = new ArrayList<>();
		projectAndEnv.setUseSNS();
		EasyMock.expect(facade.rollbackTemplatesInFolder(FilesForTesting.ORDERED_SCRIPTS_FOLDER, projectAndEnv)).andReturn(stacks);
			
		validate(CLIArgBuilder.rollbackFromDir(FilesForTesting.ORDERED_SCRIPTS_FOLDER, "-sns"));
	}

	private String validate(String[] args) {
		replayAll();
		Main main = new Main(args);

        PrintStream original = System.out;

        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(arrayOutputStream);
        System.setOut(printStream);
        int result = main.parse(factory, true);
        printStream.close();
        System.setOut(original);

		String output = new String(arrayOutputStream.toByteArray(), Charset.defaultCharset());

		verifyAll();

		assertEquals(output, 0,result);
		return output;
	}

	private void setFactoryExpectations()
			throws MissingArgumentException, CfnAssistException,
			InterruptedException {
		factory.setRegion(EnvironmentSetupForTests.getRegion());
		factory.setProject(EnvironmentSetupForTests.PROJECT);
		EasyMock.expect(factory.createFacade()).andReturn(facade);
	}

}

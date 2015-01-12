package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;

import com.amazonaws.services.cloudformation.model.Parameter;

public class CreateDiagramAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public CreateDiagramAction() {
		option = OptionBuilder.withArgName("diagrams").hasArg().withDescription("Create diagrams for VPCs in given folder").create("diagrams");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argument) throws InvalidStackParameterException,
			FileNotFoundException, IOException,
			InterruptedException, CfnAssistException, MissingArgumentException {
		DiagramCreator diagramCreator = factory.createDiagramCreator();
		Path folder = Paths.get(argument[0]);
		Recorder recorder = new FileRecorder(folder);
		diagramCreator.createDiagrams(recorder);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argumentForAction) throws CommandLineException {
		guardForNoArtifacts(artifacts);
		guardForNoBuildNumber(projectAndEnv);
		guardForSNSNotSet(projectAndEnv);
	}

	@Override
	public boolean usesProject() {
		return false;
	}

	@Override
	public boolean usesComment() {
		return false;
	}

	@Override
	public boolean usesSNS() {
		return false;
	}

}

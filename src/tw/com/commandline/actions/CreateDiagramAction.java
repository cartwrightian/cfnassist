package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.DiagramCreator;
import tw.com.pictures.dot.FileRecorder;
import tw.com.pictures.dot.Recorder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public class CreateDiagramAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public CreateDiagramAction() {
		createOptionWithArg("diagrams", "Create diagrams for VPCs in given folder");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argument) throws
            IOException,
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

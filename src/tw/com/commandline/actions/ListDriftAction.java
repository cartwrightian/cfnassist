package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.CFNClient;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ListDriftAction extends SharedAction {

	@SuppressWarnings("static-access")
	public ListDriftAction() {
		createOption("drift", "List out the stack drift status");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
					   Collection<Parameter> cfnParams, Collection<Parameter> artifacts, String... argument) throws
            IOException,
            InterruptedException, CfnAssistException, MissingArgumentException {
		AwsFacade aws = factory.createFacade();

		List<StackEntry> results = aws.listStackDrift(projectAndEnv);
		render(results);
	}
	
	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
		guardForProject(projectAndEnv);
		guardForNoBuildNumber(projectAndEnv);
		guardForNoArtifacts(artifacts);
	}
	
	private void render(List<StackEntry> results) {
		System.out.println("Stackname\tProject\tEnvironment\tDrift\tCount");
		for(StackEntry entry : results) {
			CFNClient.DriftStatus driftStatus = entry.getDriftStatus();
			System.out.println(String.format("%s\t%s\t%s\t%s\t%s",
					entry.getStackName(), 
					entry.getProject(), 
					entry.getEnvTag().getEnv(),
					driftStatus.getStackDriftStatus(),
					driftStatus.getDriftedStackResourceCount()));
		}
	}

	@Override
	public boolean usesProject() {
		return true;
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

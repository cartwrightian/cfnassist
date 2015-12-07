package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ListAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public ListAction() {
		createOption("ls", "List out the stacks");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts, String... argument) throws
            IOException,
            InterruptedException, CfnAssistException, MissingArgumentException {
		AwsFacade aws = factory.createFacade();
		List<StackEntry> results = aws.listStacks(projectAndEnv);
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
		System.out.println("Stackname\tProject\tEnvironment");
		for(StackEntry entry : results) {
			System.out.println(String.format("%s\t%s\t%s\t%s", 
					entry.getStackName(), 
					entry.getProject(), 
					entry.getEnvTag().getEnv(),
					entry.getStack().getStackStatus().toString()));
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

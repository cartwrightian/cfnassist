package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class ListAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public ListAction() {
		option = OptionBuilder.withArgName("ls").withDescription("List out the stacks").create("ls");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts, String... argument) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
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

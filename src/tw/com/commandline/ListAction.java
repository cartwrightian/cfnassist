package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
import tw.com.StackEntry;
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
	public void invoke(AwsFacade aws, ELBRepository repository,
			ProjectAndEnv projectAndEnv, String argument,
			Collection<Parameter> cfnParams) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, CfnAssistException {
		List<StackEntry> results = aws.listStacks(projectAndEnv);
		render(results);
	}
	
	@Override
	public void validate(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String argumentForAction, Collection<Parameter> cfnParams) throws CommandLineException {
		guardForProject(projectAndEnv);
		guardForNoBuildNumber(projectAndEnv);
	}
	
	private void render(List<StackEntry> results) {
		System.out.println("Stackname\tProject\tEnvironment");
		for(StackEntry entry : results) {
			System.out.println(String.format("%s\t%s\t%s", entry.getStackName(), entry.getProject(), entry.getEnvTag().getEnv()));
		}
	}

}

package tw.com.commandline;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public interface CommandLineAction {
	
	Option getOption();

	String getArgName();

	void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argument) throws
			IOException, InterruptedException, CfnAssistException, MissingArgumentException;

	void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException;

	boolean usesProject();
	boolean usesComment();
	boolean usesSNS();

}

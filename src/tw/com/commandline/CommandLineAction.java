package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.cli.Option;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.StackCreateFailed;
import tw.com.TagsAlreadyInit;
import tw.com.WrongNumberOfStacksException;

public interface CommandLineAction {
	
	Option getOption();

	String getArgName();

	void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String argument) throws InvalidParameterException, FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, TagsAlreadyInit, CannotFindVpcException, StackCreateFailed;

}

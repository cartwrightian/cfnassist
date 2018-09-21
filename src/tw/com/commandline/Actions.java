package tw.com.commandline;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.commandline.actions.*;

import java.util.LinkedList;
import java.util.List;

public class Actions {
	private static final Logger logger = LoggerFactory.getLogger(Actions.class);

	private List<CommandLineAction> actions;
	
	public Actions() {
		actions = new LinkedList<>();
		createActions();
	}

	public void addActionsTo(Options cliOptions) {
		for(CommandLineAction action : actions) {
			cliOptions.addOption(action.getOption());
		}
	}
	
	public CommandLineAction selectCorrectActionFromArgs(CommandLine cmd, HelpFormatter formatter, String executableName, Options commandLineOptions) throws MissingArgumentException {
		int count = 0;
		CommandLineAction matchingAction = null;
		StringBuilder names = new StringBuilder();
		for(CommandLineAction action : actions) {
			names.append(action.getArgName()).append(" ");
			if (cmd.hasOption(action.getArgName())) {
				matchingAction = action;
				count++;
			}	
		}

		if (count!=1) {
			String msg = "Please supply only one of " + names.toString();
			logger.error(msg);	
			formatter.printHelp(executableName, commandLineOptions);
			throw new MissingArgumentException(msg);
		}		
		return matchingAction;		
	}

	private void createActions() {
		actions.add(new FileAction());
		actions.add(new DirAction());
		actions.add(new ResetAction());
		actions.add(new InitAction());
		actions.add(new AddTagAction());
		actions.add(new ElbAction());
		actions.add(new DeleteAction());
		actions.add(new DeleteByNameAction());
		actions.add(new ListAction());
		actions.add(new S3CreateAction());
		actions.add(new S3DeleteAction());
		actions.add(new TidyOldStacksAction());
		actions.add(new CreateDiagramAction());
		actions.add(new WhitelistAction());
		actions.add(new BlacklistAction());
		actions.add(new InstancesAction());
		actions.add(new CreateKeyPairAction());
		actions.add(new SSHCommandAction());
        actions.add(new BackAction());
        actions.add(new PurgeAction());
        actions.add(new AllowHostAction());
        actions.add(new BlockHostAction());
        actions.add(new RemoveLogsAction());
        actions.add(new TagLogAction());
        actions.add(new FetchLogsAction());
	}
}

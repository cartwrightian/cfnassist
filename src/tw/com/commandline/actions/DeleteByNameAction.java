package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class DeleteByNameAction extends SharedAction {
    private static final Logger logger = LoggerFactory.getLogger(DeleteAction.class);

    @SuppressWarnings("static-access")
    public DeleteByNameAction() {
        createOptionWithArg("rm", "The base name (i.e. excluding project and env) of the stack to delete");
    }

    @Override
    public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                       String... args) throws
            IOException,
            InterruptedException, CfnAssistException, MissingArgumentException {
        String name = args[0];
        logger.info(String.format("Attempting to delete corresponding to name: %s and %s", name, projectAndEnv));
        AwsFacade aws = factory.createFacade();
        aws.deleteStackByName(name, projectAndEnv);
    }

    @Override
    public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         String... argumentForAction) throws CommandLineException {
        guardForProjectAndEnv(projectAndEnv);
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
        return true;
    }
}

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

public class AddTagAction extends SharedAction  {
    private static final Logger logger = LoggerFactory.getLogger(AddTagAction.class);

    @SuppressWarnings("static-access")
    public AddTagAction() {
        createOptionWithArgs("tag", "Set a tag on the vpc, provide tag name and tag value", 2);
    }

    @Override
    public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
                       String... args) throws IOException, InterruptedException, CfnAssistException, MissingArgumentException {
        String tagName = args[0];
        String tagValue = args[1];
        logger.info(String.format("set tag %s=%s for %s", tagName, tagValue, projectAndEnv));
        AwsFacade aws = factory.createFacade();
        aws.setTagForVpc(projectAndEnv, tagName, tagValue);
    }

    @Override
    public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
        guardForProjectAndEnv(projectAndEnv);
        guardForNoBuildNumber(projectAndEnv);
        guardForNoArtifacts(artifacts);
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

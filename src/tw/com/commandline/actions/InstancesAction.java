package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.InstanceSummary;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class InstancesAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public InstancesAction() {
		createOption("instances", "List out sumamry of instances");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts, String... argument) throws
            IOException,
            InterruptedException, CfnAssistException, MissingArgumentException {
		AwsFacade aws = factory.createFacade();
		SearchCriteria criteria = new SearchCriteria(projectAndEnv);
		List<InstanceSummary> results = aws.listInstances(criteria);
		render(results);
	}
	
	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
		guardForProject(projectAndEnv);
		guardForNoBuildNumber(projectAndEnv);
		guardForNoArtifacts(artifacts);
	}
	
	private void render(List<InstanceSummary> results) {
		System.out.println("id\tPrivate IP\tTags");
		for(InstanceSummary entry : results) {
			System.out.println(String.format("%s\t%s\t%s", 
					entry.getInstance(), 
					entry.getPrivateIP(), 
					entry.getTags()));
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

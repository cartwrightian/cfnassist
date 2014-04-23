package tw.com.ant;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.tools.ant.BuildException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.FacadeFactory;
import tw.com.ProjectAndEnv;
import tw.com.commandline.CommandLineException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class CfnAssistAntTask extends org.apache.tools.ant.Task {

	private String awsRegion;
	private String cfnProject;
	private String cfnBuildNumber = null;
	private String cfnEnv;
	private boolean snsMonitoring;
	private Collection<Param> params;
	
	private List<ActionElement> actionElements;
	
	public CfnAssistAntTask() {
		snsMonitoring = false;
		params = new LinkedList<Param>();
		actionElements = new LinkedList<ActionElement>();
	}
	
	public void setRegion(String awsRegion) {
		this.awsRegion = awsRegion;
	}
	
	public void setProject(String cfnProject) {
		this.cfnProject = cfnProject;
	}
	
	public void setEnv(String cfnEnv) {
		this.cfnEnv = cfnEnv;
	}
	
	public void setBuildNumber(String cfnBuildNumber) {
		this.cfnBuildNumber  = cfnBuildNumber;
	}
	
	public void setSns(boolean useSnsMonitoring) {
		this.snsMonitoring = useSnsMonitoring;
	}
	
	// addConfigured is looked for by ant, Templates is the name of the nested element
	public void addConfiguredTemplates(TemplatesElement fileElement) {
		actionElements.add(fileElement);
	}
	
	// addConfigured is looked for by ant, Delete is the name of the nested element
	public void addConfiguredDelete(DeleteElement deleteElement) {
		actionElements.add(deleteElement);
	}
	
	// addConfigured is looked for by ant
	public void addConfiguredRollback(RollbackElement rollbackElement) {
		actionElements.add(rollbackElement);
	}
	
	public void execute() {
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(cfnProject, cfnEnv);
		if (snsMonitoring) {
			projectAndEnv.setUseSNS();
		}
		if (cfnBuildNumber!=null) {
			projectAndEnv.addBuildNumber(cfnBuildNumber);
		}
		Region region = RegionUtils.getRegion(awsRegion);

		Collection<Parameter> cfnParameters = new LinkedList<Parameter>();
		for(Param param : params) {
			cfnParameters.add(param.getParamter());
		}		
		try {
			FacadeFactory factory = new FacadeFactory(region,cfnProject);
			AwsFacade aws = factory.createFacade(projectAndEnv.useSNS());
			ELBRepository repository = factory.createElbRepo();
			for(ActionElement element : actionElements) {
				element.execute(aws, projectAndEnv, cfnParameters, repository);
			}
		} catch (IOException | MissingArgumentException
				| InvalidParameterException | InterruptedException | CfnAssistException | CommandLineException innerException) {
			throw new BuildException(innerException);
		}
	}
	
	 public Param createParam() {                                 
		 Param param = new Param();
		 params.add(param);
		 return param;
	 }
		 
	public class Param {
		private String name;
		private String value;

		public Param() {		
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public void setValue(String value) {
			this.value = value;
		}
		
		public Parameter getParamter() {
			Parameter param = new Parameter();
			param.setParameterKey(name);
			param.setParameterValue(value);
			return param;
		}
	}
}

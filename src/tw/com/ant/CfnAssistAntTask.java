package tw.com.ant;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
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
	private Collection<Param> params;
	
	public CfnAssistAntTask() {
		params = new LinkedList<Param>();
	}
	
	private TemplatesElement fileElement;

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
	
	public void addConfiguredTemplates(TemplatesElement fileElement) {
		this.fileElement = fileElement;
	}
	
	public void execute() {
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(cfnProject, cfnEnv);
		if (cfnBuildNumber!=null) {
			projectAndEnv.addBuildNumber(cfnBuildNumber);
		}
		Region region = RegionUtils.getRegion(awsRegion);

		Collection<Parameter> cfnParameters = new LinkedList<Parameter>();
		for(Param param : params) {
			cfnParameters.add(param.getParamter());
		}
		AwsFacade aws = new FacadeFactory().createFacace(region);
		try {		
			fileElement.execute(aws, projectAndEnv, cfnParameters);
		} catch (IOException
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

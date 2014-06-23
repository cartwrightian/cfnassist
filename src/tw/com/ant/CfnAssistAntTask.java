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
	private String bucketName;
	private boolean snsMonitoring;
	private Collection<Param> params;
	private Collection<Param> artifactParams;
	
	private List<ActionElement> actionElements;
	
	public CfnAssistAntTask() {
		snsMonitoring = false;
		params = new LinkedList<Param>();
		artifactParams = new LinkedList<Param>();
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
	
	public void setBucketName(String bucketName) {
		this.bucketName  = bucketName;
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
	
	// addConfigured is looked for by ant, Rollback is name of element
	public void addConfiguredRollback(RollbackElement rollbackElement) {
		actionElements.add(rollbackElement);
	}
	
	// addConfigured is looked for by ant, ELBUpdate is name of the element
	public void addConfiguredELBUpdate(ELBUpdateElement elbUpdateElement) {
		actionElements.add(elbUpdateElement);
	}
	
	public void execute() {
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(cfnProject, cfnEnv);
		if (snsMonitoring) {
			projectAndEnv.setUseSNS();
		}
		if (cfnBuildNumber!=null) {
			projectAndEnv.addBuildNumber(cfnBuildNumber);
		}
		if (bucketName!=null) {
			projectAndEnv.setS3Bucket(bucketName);
		}
		Region region = RegionUtils.getRegion(awsRegion);

		Collection<Parameter> cfnParameters = createParameterList();
		Collection<Parameter> artifacts = createArtifactList();	
		FacadeFactory factory = new FacadeFactory(region,cfnProject,projectAndEnv.useSNS());
		try {
			for(ActionElement element : actionElements) {
				element.execute(factory, projectAndEnv, cfnParameters, artifacts);
			}
		} catch (IOException | MissingArgumentException
				| InvalidParameterException | InterruptedException | CfnAssistException | CommandLineException innerException) {
			throw new BuildException(innerException);
		}
	}

	private Collection<Parameter> createParameterList() {
		Collection<Parameter> cfnParameters = new LinkedList<Parameter>();
		for(Param param : params) {
			cfnParameters.add(param.getParamter());
		}
		return cfnParameters;
	}

	private Collection<Parameter> createArtifactList() {
		Collection<Parameter> uploadParams = new LinkedList<Parameter>();
		for(Param upload : artifactParams) {
			uploadParams.add(upload.getParamter());
		}
		return uploadParams;
	}
	
	 public Param createParam() {                                 
		 Param param = new Param();
		 params.add(param);
		 return param;
	 }
	 
	 public Param createArtifact() {                                 
		 Param param = new Param();
		 artifactParams.add(param);
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

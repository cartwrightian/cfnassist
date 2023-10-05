package tw.com.ant;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.tools.ant.BuildException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class CfnAssistAntTask extends org.apache.tools.ant.Task {

	private String cfnProject;
	private Integer cfnBuildNumber = null;
	private String cfnEnv;
	private String bucketName;
	private boolean snsMonitoring;
	private boolean capabilityIAM;
	private final Collection<Param> params;
	private final Collection<Param> artifactParams;
	
	private final List<ActionElement> actionElements;
	
	public CfnAssistAntTask() {
		snsMonitoring = false;
		params = new LinkedList<>();
		artifactParams = new LinkedList<>();
		actionElements = new LinkedList<>();
	}

	public void setProject(String cfnProject) {
		this.cfnProject = cfnProject;
	}
	
	public void setEnv(String cfnEnv) {
		this.cfnEnv = cfnEnv;
	}
	
	public void setBuildNumber(String cfnBuildNumber) {
		this.cfnBuildNumber  = Integer.parseInt(cfnBuildNumber);
	}
	
	public void setBucketName(String bucketName) {
		this.bucketName  = bucketName;
	}
	
	public void setSns(boolean useSnsMonitoring) {
		this.snsMonitoring = useSnsMonitoring;
	}
	
	public void setCapabilityIAM(boolean capabilityIAM) {
		this.capabilityIAM = capabilityIAM;
	}
	
	// NOTE
	// addConfigured is looked for by ant, the rest is the name of the Element
	
	public void addConfiguredTemplates(TemplatesElement fileElement) {
		actionElements.add(fileElement);
	}
	
	public void addConfiguredDelete(DeleteElement deleteElement) {
		actionElements.add(deleteElement);
	}

	public void addConfiguredInit(InitElement initElement) { actionElements.add(initElement); }

	public void addConfiguredSetTag(SetTagAction setTagAction) { actionElements.add(setTagAction);}
	
	public void addConfiguredPurge(PurgeElement purgeElement) {
		actionElements.add(purgeElement);
	}
	
	public void addConfiguredELBUpdate(ELBUpdateElement elbUpdateElement) {
		actionElements.add(elbUpdateElement);
	}

	public void addConfiguredTargetGroupUpdate(TargetGroupUpdateElement targetGroupUpdateElement) {
		actionElements.add(targetGroupUpdateElement);
	}
	
	public void addConfiguredS3Create(S3Create s3create) {
		actionElements.add(s3create);
	}
	
	public void addConfiguredS3Delete(S3Delete s3delete) {
		actionElements.add(s3delete);
	}
	
	public void addConfiguredTidyStacks(TidyStacksElement tidyStacksElement) {
		actionElements.add(tidyStacksElement);
	}
	
	public void addConfiguredDiagrams(DiagramsElement diagramsElement) {
		actionElements.add(diagramsElement);
	}
	
	public void addConfiguredAllowCurrentIP(AllowCurrentIPElement allowCurrentIPElement) {
		actionElements.add(allowCurrentIPElement);
	}
	
	public void addConfiguredBlockCurrentIP(BlockCurrentIPElement blockCurrentIPElement) { actionElements.add(blockCurrentIPElement); }

	public void addConfiguredAllowHost(AllowhostElement allowhostElement) { actionElements.add(allowhostElement); }

    public void addConfiguredBlockHost(BlockhostElement blockhostElement) { actionElements.add(blockhostElement); }
	
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
		if (capabilityIAM) {
			projectAndEnv.setUseCapabilityIAM();
		}

		Collection<Parameter> cfnParameters = createParameterList();
		Collection<Parameter> artifacts = createArtifactList();	
		
		FacadeFactory factory = new FacadeFactory();
		factory.setProject(cfnProject);
		if (snsMonitoring) {
			factory.setSNSMonitoring();
		}
		
		try {
			for(ActionElement element : actionElements) {
				element.execute(factory, projectAndEnv, cfnParameters, artifacts);
			}
		} catch (IOException | MissingArgumentException |  
				InterruptedException | 
				CfnAssistException | 
				CommandLineException innerException) {
			throw new BuildException(innerException);
		}
	}

	private Collection<Parameter> createParameterList() {
		Collection<Parameter> cfnParameters = new LinkedList<>();
		for(Param param : params) {
			cfnParameters.add(param.getParamter());
		}
		return cfnParameters;
	}

	private Collection<Parameter> createArtifactList() {
		Collection<Parameter> uploadParams = new LinkedList<>();
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
			Parameter param = Parameter.builder().parameterKey(name).parameterValue(value).build();
			return param;
		}
	}
}

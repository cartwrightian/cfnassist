package tw.com.ant;

import java.io.IOException;

import org.apache.tools.ant.BuildException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.StackCreateFailed;
import tw.com.WrongNumberOfStacksException;

public class CfnAssistAntTask extends org.apache.tools.ant.Task {

	private String awsRegion;
	private String cfnProject;
	private String cfnEnv;
	
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
	
	public void addConfiguredTemplates(TemplatesElement fileElement) {
		this.fileElement = fileElement;
	}
	
	public void execute() {
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(cfnProject, cfnEnv);
		Region region = RegionUtils.getRegion(awsRegion);
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();

		AwsFacade aws = new AwsFacade(credentialsProvider , region);
		try {
			fileElement.execute(aws, projectAndEnv);
		} catch (IOException
				| InvalidParameterException | WrongNumberOfStacksException
				| InterruptedException | CannotFindVpcException | StackCreateFailed innerException) {
			throw new BuildException(innerException);
		}
	}
}

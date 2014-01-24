package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

public interface AwsProvider {
	
	List<TemplateParameter> validateTemplate(File file) throws FileNotFoundException, IOException;
	String applyTemplate(File file, ProjectAndEnv projAndEnv) throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed;
	String applyTemplate(File file, ProjectAndEnv projAndEnv,  Collection<Parameter> parameters) throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed;
	
	ArrayList<String> applyTemplatesFromFolder(String folderPath, ProjectAndEnv projAndEnv) throws InvalidParameterException, FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, CannotFindVpcException, StackCreateFailed;
	ArrayList<String> applyTemplatesFromFolder(String folderPath, ProjectAndEnv projAndEnv, Collection<Parameter> cfnParams) throws InvalidParameterException, FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, CannotFindVpcException, StackCreateFailed;
	
	String createStackName(File templateFile, ProjectAndEnv projAndEnv);
	void deleteStack(String stackName);
	
	String waitForDeleteFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException;
	String waitForCreateFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed;
	
	List<Parameter> fetchAutopopulateParametersFor(File file, EnvironmentTag envTag, List<TemplateParameter> declaredParam) throws FileNotFoundException, IOException, InvalidParameterException;
	
	void resetDeltaIndex(ProjectAndEnv projAndEnv) throws CannotFindVpcException;
	void setDeltaIndex(ProjectAndEnv projAndEnv, Integer index) throws CannotFindVpcException;
	int getDeltaIndex(ProjectAndEnv projAndEnv) throws CannotFindVpcException;
	public void initEnvAndProjectForVPC(String targetVpcId, ProjectAndEnv projectAndEnvToSet) throws CfnAssistException;
	public void initEnvAndProjectForStack(String stackName, ProjectAndEnv projectAndEnv) throws CfnAssistException;
	
}

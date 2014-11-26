package tw.com.repository;

import java.util.List;

import com.amazonaws.services.elasticloadbalancing.model.Instance;

import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.WrongNumberOfInstancesException;

public interface ResourceRepository {

	public String findPhysicalIdByLogicalId(EnvironmentTag envTag,String logicalId);

	public List<String> getAllInstancesFor(ProjectAndEnv projAndEnv);

	public List<Instance> getAllInstancesMatchingType(ProjectAndEnv projAndEnv, String typeTag) throws WrongNumberOfInstancesException;
	
	List<String> getInstancesFor(String Stackname);

}
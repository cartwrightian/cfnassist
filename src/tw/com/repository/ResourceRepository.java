package tw.com.repository;

import java.util.List;


import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;

public interface ResourceRepository {

	public String findPhysicalIdByLogicalId(EnvironmentTag envTag,String logicalId);

	public List<String> getAllInstancesFor(SearchCriteria criteria) throws CfnAssistException;

	public List<Instance> getAllInstancesMatchingType(SearchCriteria criteria, String typeTag) throws CfnAssistException;
	
	List<String> getInstancesFor(String Stackname);

}
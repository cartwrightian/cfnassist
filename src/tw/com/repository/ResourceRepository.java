package tw.com.repository;

import java.util.List;

import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;

public interface ResourceRepository {

	public abstract String findPhysicalIdByLogicalId(EnvironmentTag envTag,
			String logicalId);

	public abstract List<String> getInstancesFor(ProjectAndEnv projAndEnv);

}
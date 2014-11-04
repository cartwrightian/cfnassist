package tw.com.parameters;

import java.util.Collection;
import java.util.List;

import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

public class CfnBuiltInParams extends PopulatesParameters {

	private String vpcId;

	public CfnBuiltInParams(String vpcId) {
		this.vpcId = vpcId;
	}

	@Override
	public void addParameters(Collection<Parameter> result,
			List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv) {
		addParameterTo(result, declaredParameters, AwsFacade.PARAMETER_ENV, projAndEnv.getEnv());
		addParameterTo(result, declaredParameters, AwsFacade.PARAMETER_VPC, vpcId);
		if (projAndEnv.hasBuildNumber()) {
			addParameterTo(result, declaredParameters, AwsFacade.PARAMETER_BUILD_NUMBER, projAndEnv.getBuildNumber());
		}	
	}
}

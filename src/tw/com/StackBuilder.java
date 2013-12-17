package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import com.amazonaws.services.cloudformation.model.Parameter;

public class StackBuilder  {
	private AwsProvider awsProvider;
	private File templateFile;
	Collection<Parameter> parameters;
	private String env;
	private String project;

	public StackBuilder(AwsProvider awsProvider, String project, String env, File templateFile) {
        parameters = new HashSet<Parameter>();
	
        this.env = env;
        this.project = project;
		this.awsProvider = awsProvider;
		this.templateFile = templateFile;
	}

	public StackBuilder addParameter(String key, String value) {
		Parameter envParameter = new Parameter();
		envParameter.setParameterKey(key);
		envParameter.setParameterValue(value);
		parameters.add(envParameter);
		return this;	
	}

	public String createStack() throws FileNotFoundException, IOException, InvalidParameterException {
		return awsProvider.applyTemplate(templateFile, project, env, parameters);
	}

}

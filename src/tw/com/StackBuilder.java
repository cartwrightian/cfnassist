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

	public StackBuilder(AwsProvider awsProvider, File templateFile) {
        parameters = new HashSet<Parameter>();
	
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

	public void createStack(String stackName) throws FileNotFoundException, IOException {
		awsProvider.applyTemplate(templateFile, stackName, parameters);
	}


}

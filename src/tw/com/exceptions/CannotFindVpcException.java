package tw.com.exceptions;

import tw.com.entity.ProjectAndEnv;

@SuppressWarnings("serial")
public class CannotFindVpcException extends CfnAssistException {

	public CannotFindVpcException(ProjectAndEnv projAndEnv) {
		super("CannotFindVpcException " + projAndEnv.toString());
	}

	public CannotFindVpcException(String id) {
		super("CannotFindVpcException " + id);
	}

}

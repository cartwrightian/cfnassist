package tw.com;

@SuppressWarnings("serial")
public class CannotFindVpcException extends Exception {

	private String id;

	public CannotFindVpcException(ProjectAndEnv projAndEnv) {
		this.id = projAndEnv.toString();
	}

	public CannotFindVpcException(String id) {
		this.id=id;
	}

	@Override
	public String toString() {
		return "CannotFindVpcException " + id;
	}
	
	

}

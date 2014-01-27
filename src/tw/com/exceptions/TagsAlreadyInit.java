package tw.com.exceptions;


@SuppressWarnings("serial")
public class TagsAlreadyInit extends CfnAssistException {
	private String vpcId;

	public TagsAlreadyInit(String vpcId) {
		super();
		this.vpcId = vpcId;
	}

	@Override
	public String toString() {
		return "TagsAlreadyInit [vpcId=" + vpcId + "]";
	}
	
}

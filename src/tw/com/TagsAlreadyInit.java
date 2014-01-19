package tw.com;

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

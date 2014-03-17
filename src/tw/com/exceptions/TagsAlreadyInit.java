package tw.com.exceptions;


@SuppressWarnings("serial")
public class TagsAlreadyInit extends CfnAssistException {

	public TagsAlreadyInit(String vpcId) {
		super("TagsAlreadyInit [vpcId=" + vpcId + "]");

	}
	
}

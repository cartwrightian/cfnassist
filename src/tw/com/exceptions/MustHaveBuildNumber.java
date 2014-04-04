package tw.com.exceptions;

@SuppressWarnings("serial")
public class MustHaveBuildNumber extends CfnAssistException {

	public MustHaveBuildNumber() {
		super("Must provide a build number");
	}

}

package tw.com.entity;

import org.apache.commons.net.util.SubnetUtils;

import tw.com.exceptions.CfnAssistException;

public class Cidr {
	private static final String DEFAULT_CIDR = "0.0.0.0/0";
	private SubnetUtils subnet;
	private boolean isDefault;
	
	private Cidr(String string) {
		subnet = new SubnetUtils(string);
	}

	private Cidr(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public boolean isDefault() {
		return isDefault;
	}
	
	public static Cidr Default() {
		return new Cidr(true);
	}
	
	@Override
	public String toString() {
		if (isDefault()) {
			return DEFAULT_CIDR;
		}
		return subnet.getInfo().getCidrSignature();
	}

	public static Cidr parse(String string) throws CfnAssistException {
		if (DEFAULT_CIDR.equals(string)) {
			return Default();
		}
		try {
			return new Cidr(string);
		}
		catch(IllegalArgumentException exception) {
			throw new CfnAssistException("Could not parse cidr " + string);	
		}
	}

}

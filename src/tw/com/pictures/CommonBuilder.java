package tw.com.pictures;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.CommonElements;
import com.amazonaws.services.ec2.model.IpPermission;

public class CommonBuilder {
	private Set<String> addedIpPerms;

	public CommonBuilder() {
		addedIpPerms = new HashSet<>();
	}
	
	protected void addSecGroupInboundPerms(CommonElements diagram, String groupId, IpPermission perms) throws CfnAssistException {
		String range = createRange(perms);
		String uniqueId = createUniqueId(groupId, perms, range, "in");
		if (haveAddedPerm(uniqueId)) {
			return;
		}
		
		addedIpPerms.add(uniqueId);
		String label = createLabel(perms);
		diagram.addPortRange(uniqueId, range);	
		diagram.connectWithLabel(uniqueId, groupId, label);		
	}
	
	protected void addSecGroupOutboundPerms(CommonElements diagram, String groupId, IpPermission perms) throws CfnAssistException {
		String range = createRange(perms);
		String uniqueId = createUniqueId(groupId, perms, range, "out");
		if (haveAddedPerm(uniqueId)) {
			return;
		}
		
		addedIpPerms.add(uniqueId);
		String label = createLabel(perms);
		diagram.addPortRange(uniqueId, range);	
		diagram.connectWithLabel(groupId, uniqueId, label);
	}
		
	private String createRange(IpPermission perms) {
		Integer to = perms.getToPort();
		Integer from = perms.getFromPort();
		if (to.equals(from)) {
			if (to.equals(-1)) {
				return "all";
			}
			return to.toString();
		}
		return String.format("%s-%s",from, to);
	}
	
	private String createUniqueId(String groupId, IpPermission perms, String range, String dir) {
		return String.format("%s_%s_%s_%s", groupId, perms.getIpProtocol(), range, dir);
	}
	
	private String createLabel(IpPermission perms) {
		List<String> ipRanges = perms.getIpRanges();
		if (ipRanges.isEmpty()) {
			return String.format("[%s]", perms.getIpProtocol());
		}
		
		StringBuilder rangesPart = new StringBuilder();
		for (String range : ipRanges) {
			if (rangesPart.length()!=0) {
				rangesPart.append(",\n");
			}
			rangesPart.append(range);
		}
		return String.format("(%s)\n[%s]", rangesPart.toString(),perms.getIpProtocol());
	}
	
	private boolean haveAddedPerm(String uniqueId) {
		return addedIpPerms.contains(uniqueId);
	}

}

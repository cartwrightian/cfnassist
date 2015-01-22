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
		if (to==null) {
			if (from==null) {
				return "all";
			}
			return from.toString();
		}
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
		String ipProtocol = perms.getIpProtocol();
		if (ipProtocol.equals("-1")) {
			ipProtocol = "all";
		}
		
		if (ipRanges.isEmpty()) {
			return String.format("[%s]", ipProtocol);
		}
		
		return String.format("(%s)\n[%s]", ipRangesIntoTextList(ipRanges) ,ipProtocol);
	}

	private String ipRangesIntoTextList(List<String> ipRanges) {
		StringBuilder rangesPart = new StringBuilder();
		for (String range : ipRanges) {
			if (rangesPart.length()!=0) {
				rangesPart.append(",\n");
			}
			if (range.equals("0.0.0.0/0")) {
				range = "all";
			}
			rangesPart.append(range);
		}
		return rangesPart.toString();
	}
	
	private boolean haveAddedPerm(String uniqueId) {
		return addedIpPerms.contains(uniqueId);
	}
	

	String formRouteTableIdForDiagram(String subnetId, String routeTableId) {
		return String.format("%s_%s", subnetId, routeTableId);
	}

}

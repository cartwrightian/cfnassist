package tw.com.entity;

import java.util.List;

import com.amazonaws.services.ec2.model.Tag;

public class InstanceSummary {

	private String instanceId;
	private String privateIP;
	private List<Tag> tags;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((instanceId == null) ? 0 : instanceId.hashCode());
		result = prime * result
				+ ((privateIP == null) ? 0 : privateIP.hashCode());
		result = prime * result + ((tags == null) ? 0 : tags.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InstanceSummary other = (InstanceSummary) obj;
		if (instanceId == null) {
			if (other.instanceId != null)
				return false;
		} else if (!instanceId.equals(other.instanceId))
			return false;
		if (privateIP == null) {
			if (other.privateIP != null)
				return false;
		} else if (!privateIP.equals(other.privateIP))
			return false;
		if (tags == null) {
			if (other.tags != null)
				return false;
		} else if (!tags.equals(other.tags))
			return false;
		return true;
	}

	public InstanceSummary(String instanceId, String privateIP, List<Tag> tags) {
		this.instanceId = instanceId;
		this.privateIP = privateIP;
		this.tags = tags;
	}

	public String getInstance() {
		return instanceId;
	}

	public String getPrivateIP() {
		return privateIP;
	}
	
	public String getTags() {
		StringBuilder builder = new StringBuilder();
		for(Tag tag : tags) {
			if (builder.length()!=0) {
				builder.append(",");
			}
			builder.append(String.format("%s=%s",tag.getKey(),tag.getValue()));
		}
		return builder.toString();
	}
}

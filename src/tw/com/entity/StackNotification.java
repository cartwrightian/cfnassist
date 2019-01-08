package tw.com.entity;

import software.amazon.awssdk.services.cloudformation.model.StackStatus;

public class StackNotification {

	private StackStatus status;
	private String resourceName;
	private String resourceId;
	private String resourceType;
	private String statusReason;
	
	public StackNotification(String stackName, StackStatus status, String stackId, String resourceType, String statusReason) {
		this.status = status;
		this.resourceName = stackName;
		this.resourceId = stackId;
		this.resourceType = resourceType;
		this.statusReason = statusReason;
	}
	
	public static StackNotification parseNotificationMessage(String notificationMessage) {
		String[] parts = notificationMessage.split("\n");
		StackStatus status = StackStatus.UNKNOWN_TO_SDK_VERSION;
		String foundName="";
		String stackId="";
		String type="";
		String reason="";
		for(int i=0; i<parts.length; i++) {
			String[] elements = parts[i].split("=");
			String key = elements[0];
			if (elements.length==2) {
				String containsValue = elements[1];
				switch (key) { 
					case "StackName": foundName=extractValue(containsValue);
						break;
					case "ResourceStatus": status=StackStatus.fromValue(extractValue(containsValue));
						break;
					case "StackId": stackId=extractValue(containsValue);
						break;
					case "ResourceType": type=extractValue(containsValue);
						break;
					case "ResourceStatusReason": reason=extractValue(containsValue);
						break;
				}
			}
		}
		return new StackNotification(foundName,status,stackId,type,reason);
	}
	
	private static String extractValue(String value) {
		return value.replace('\'',' ').trim();
	}

	public StackStatus getStatus() {
		return status;
	}

	public String getStackName() {
		return resourceName;
	}

	public String getStackId() {
		return resourceId;
	}

	public String getResourceType() {
		return resourceType;
	}

	public String getStatusReason() {
		return statusReason;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((resourceId == null) ? 0 : resourceId.hashCode());
		result = prime * result
				+ ((resourceName == null) ? 0 : resourceName.hashCode());
		result = prime * result
				+ ((resourceType == null) ? 0 : resourceType.hashCode());
		result = prime * result + ((status == null) ? 0 : status.hashCode());
		result = prime * result
				+ ((statusReason == null) ? 0 : statusReason.hashCode());
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
		StackNotification other = (StackNotification) obj;
		if (resourceId == null) {
			if (other.resourceId != null)
				return false;
		} else if (!resourceId.equals(other.resourceId))
			return false;
		if (resourceName == null) {
			if (other.resourceName != null)
				return false;
		} else if (!resourceName.equals(other.resourceName))
			return false;
		if (resourceType == null) {
			if (other.resourceType != null)
				return false;
		} else if (!resourceType.equals(other.resourceType))
			return false;
		if (status == null) {
			if (other.status != null)
				return false;
		} else if (!status.equals(other.status))
			return false;
		if (statusReason == null) {
			if (other.statusReason != null)
				return false;
		} else if (!statusReason.equals(other.statusReason))
			return false;
		return true;
	}

}

package tw.com.entity;

public class StackNotification {

	private String status;
	private String resourceName;
	private String resourceId;
	private String resourceType;
	private String statusReason;
	
	private StackNotification(String stackName, String status, String stackId, String resourceType, String statusReason) {
		this.status = status;
		this.resourceName = stackName;
		this.resourceId = stackId;
		this.resourceType = resourceType;
		this.statusReason = statusReason;
	}
	
	public static StackNotification parseNotificationMessage(String notificationMessage) {
		String[] parts = notificationMessage.split("\n");
		String status="";
		String foundName="";
		String stackId="";
		String type="";
		String reason="";
		for(int i=0; i<parts.length; i++) {
			String[] elements = parts[i].split("=");
			String key = elements[0];
			String containsValue = elements[1];
			switch (key) { 
				case "StackName": foundName=extractValue(containsValue);
					break;
				case "ResourceStatus": status=extractValue(containsValue);
					break;
				case "StackId": stackId=extractValue(containsValue);
					break;
				case "ResourceType": type=extractValue(containsValue);
					break;
				case "ResourceStatusReason": reason=extractValue(containsValue);
					break;
			}

		}
		return new StackNotification(foundName,status,stackId,type,reason);
	}
	
	private static String extractValue(String value) {
		return value.replace('\'',' ').trim();
	}


	public String getStatus() {
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

}

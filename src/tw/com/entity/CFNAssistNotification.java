package tw.com.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.iam.model.User;

import java.io.IOException;

public class CFNAssistNotification {
	
	private String stackName;
	private String stackStatus;
	private String userId;
	private String userName;
	
	public CFNAssistNotification(String stackName, String stackStatus, User user) {
		this.stackName = stackName;
		this.stackStatus = stackStatus;
		if (user!=null) {
			this.userId = user.userId();
			this.setUserName(user.userName());
		}	
	}
	
	// for JSON deserialisation
	protected CFNAssistNotification() {
	}
	
	// for JSON deserialisation
	public void setStackName(String stackName) {
		this.stackName = stackName;
	}
	
	// for JSON deserialisation
	public void setStackStatus(String stackStatus) {
		this.stackStatus = stackStatus;
	}
	
	// for JSON deserialisation
	public void setUserName(String userName) {
		this.userName = userName;
	}
	
	// for JSON deserialisation
	public void setUserId(String userId) {
		this.userId = userId;
	}
	
	public String getStackStatus() {
		return stackStatus;
	}

	public String getUserName() {
		return userName;
	}
	
	public String getStackName() {
		return stackName;
	}

	public String getUserId() {
		return userId;
	}
			
	public static String toJSON(CFNAssistNotification notif) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(notif);
	}
	
	public static CFNAssistNotification fromJSON(String json) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(json, CFNAssistNotification.class);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((stackName == null) ? 0 : stackName.hashCode());
		result = prime * result
				+ ((stackStatus == null) ? 0 : stackStatus.hashCode());
		result = prime * result + ((userId == null) ? 0 : userId.hashCode());
		result = prime * result
				+ ((userName == null) ? 0 : userName.hashCode());
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
		CFNAssistNotification other = (CFNAssistNotification) obj;
		if (stackName == null) {
			if (other.stackName != null)
				return false;
		} else if (!stackName.equals(other.stackName))
			return false;
		if (stackStatus == null) {
			if (other.stackStatus != null)
				return false;
		} else if (!stackStatus.equals(other.stackStatus))
			return false;
		if (userId == null) {
			if (other.userId != null)
				return false;
		} else if (!userId.equals(other.userId))
			return false;
		if (userName == null) {
			if (other.userName != null)
				return false;
		} else if (!userName.equals(other.userName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "CFNAssistNotification [stackName=" + stackName
				+ ", stackStatus=" + stackStatus + ", userId=" + userId
				+ ", userName=" + userName + "]";
	}

}

package tw.com.entity;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CFNAssistNotification {
	
	private String stackName;
	private String stackStatus;
	
	public CFNAssistNotification(String stackName, String stackStatus) {
		this.stackName = stackName;
		this.stackStatus = stackStatus;
	}
	
	public String getStackName() {
		return stackName;
	}
	
	// for JSON deserialisation
	protected CFNAssistNotification() {
	}
	
	// for JSON deserialisation
	public void setStackName(String stackName) {
		this.stackName = stackName;
	}
	
	public String getStackStatus() {
		return stackStatus;
	}
	
	// for JSON deserialisation
	public void setStackStatus(String stackStatus) {
		this.stackStatus = stackStatus;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((stackName == null) ? 0 : stackName.hashCode());
		result = prime * result
				+ ((stackStatus == null) ? 0 : stackStatus.hashCode());
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
		return true;
	}
	
	public static String toJSON(CFNAssistNotification notif) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(notif);
	}
	
	public static CFNAssistNotification fromJSON(String json) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(json, CFNAssistNotification.class);
	}

}

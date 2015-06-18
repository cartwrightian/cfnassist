package tw.com.providers;

import tw.com.entity.CFNAssistNotification;
import tw.com.exceptions.CfnAssistException;

public interface NotificationSender {
	public String sendNotification(CFNAssistNotification notification) throws CfnAssistException;
}

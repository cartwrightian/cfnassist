package tw.com.providers;

import tw.com.entity.CFNAssistNotification;
import tw.com.exceptions.CfnAssistException;

public interface NotificationSender {
	String sendNotification(CFNAssistNotification notification) throws CfnAssistException;
}

package tw.com;

import java.util.List;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.entity.StackNotification;

public interface NotificationProvider extends ProvidesMonitoringARN {

	public abstract void init() throws MissingArgumentException;

	public abstract List<StackNotification> receiveNotifications();

	public abstract boolean isInit();

}
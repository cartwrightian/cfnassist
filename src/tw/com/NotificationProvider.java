package tw.com;

import java.util.List;

import org.apache.commons.cli.MissingArgumentException;

public interface NotificationProvider extends ProvidesMonitoringARN {

	public abstract void init() throws MissingArgumentException;

	public abstract List<StackNotification> receiveNotifications();

	public abstract boolean isInit();

}
package tw.com;

import java.util.List;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.entity.StackNotification;
import tw.com.exceptions.FailedToCreateQueueException;
import tw.com.exceptions.NotReadyException;

public interface NotificationProvider extends ProvidesMonitoringARN {

	public abstract void init() throws MissingArgumentException, FailedToCreateQueueException, InterruptedException;

	public abstract List<StackNotification> receiveNotifications() throws NotReadyException;

	public abstract boolean isInit();

}
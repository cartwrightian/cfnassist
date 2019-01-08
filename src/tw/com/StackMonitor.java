package tw.com;

import software.amazon.awssdk.services.cloudformation.model.StackStatus;

public abstract class StackMonitor implements MonitorStackEvents {
	public static final StackStatus[] DELETE_ABORTS = { StackStatus.DELETE_FAILED };
	public static final StackStatus[] CREATE_ABORTS = { StackStatus.CREATE_FAILED, StackStatus.ROLLBACK_IN_PROGRESS };
	public static final StackStatus[] ROLLBACK_ABORTS = { StackStatus.ROLLBACK_FAILED };
	public static final StackStatus[] UPDATE_ABORTS = { StackStatus.UPDATE_ROLLBACK_IN_PROGRESS } ;

}
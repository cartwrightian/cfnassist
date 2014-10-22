package tw.com;

import com.amazonaws.services.cloudformation.model.StackStatus;

public abstract class StackMonitor implements MonitorStackEvents {
	public static final String[] DELETE_ABORTS = { StackStatus.DELETE_FAILED.toString() };
	public static final String[] CREATE_ABORTS = { StackStatus.CREATE_FAILED.toString(), StackStatus.ROLLBACK_IN_PROGRESS.toString() };
	public static final String[] ROLLBACK_ABORTS = { StackStatus.ROLLBACK_FAILED.toString() };

	// TODO check this list
	public static final String[] UPDATE_ABORTS = { StackStatus.UPDATE_ROLLBACK_IN_PROGRESS.toString() } ;

}
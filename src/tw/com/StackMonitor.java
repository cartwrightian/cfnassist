package tw.com;

import com.amazonaws.services.cloudformation.model.StackStatus;

public abstract class StackMonitor implements MonitorStackEvents {
	protected static final String[] DELETE_ABORTS = { StackStatus.DELETE_FAILED.toString() };
	protected static final String[] CREATE_ABORTS = { StackStatus.CREATE_FAILED.toString(), StackStatus.ROLLBACK_IN_PROGRESS.toString() };
	protected static final String[] ROLLBACK_ABORTS = { StackStatus.ROLLBACK_FAILED.toString() };

}
package tw.com;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import tw.com.repository.StackRepository;

import java.util.List;

public abstract class StackMonitor implements MonitorStackEvents {
	private static final Logger logger = LoggerFactory.getLogger(StackMonitor.class);

	protected final StackRepository stackRepository;

	public static final StackStatus[] DELETE_ABORTS = { StackStatus.DELETE_FAILED };
	public static final StackStatus[] CREATE_ABORTS = { StackStatus.CREATE_FAILED, StackStatus.ROLLBACK_IN_PROGRESS };
	public static final StackStatus[] ROLLBACK_ABORTS = { StackStatus.ROLLBACK_FAILED };
	public static final StackStatus[] UPDATE_ABORTS = { StackStatus.UPDATE_ROLLBACK_IN_PROGRESS } ;

	protected StackMonitor(StackRepository stackRepository) {
		this.stackRepository = stackRepository;
	}

	// TODO StackNameAndId
	protected void logStackEvents(String stackName) {
		final List<StackEvent> stackEvents = stackRepository.getStackEvents(stackName);
		if (stackEvents.isEmpty()) {
			logger.error("Not stack events for " + stackName);
			return;
		}
		for(StackEvent event : stackEvents) {
			logger.info(event.toString());
		}
	}
}
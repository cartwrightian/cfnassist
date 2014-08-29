package tw.com;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;

public class UploadProgressListener implements ProgressListener {
	private static final Logger logger = LoggerFactory.getLogger(UploadProgressListener.class);

	@Override
	public void progressChanged(ProgressEvent progressEvent) {
		ProgressEventType eventType = progressEvent.getEventType();
		
		switch(eventType) {
		case REQUEST_BYTE_TRANSFER_EVENT:
				logger.info("Sent " + progressEvent.getBytesTransferred() + " bytes");
			break;
		case TRANSFER_COMPLETED_EVENT:
				logger.info("Transfer finished");
			break;
		case TRANSFER_FAILED_EVENT:
				logger.error("Transfer failed");
			break;
		case TRANSFER_STARTED_EVENT:
				logger.info("Transfer started");
			break;
		default:
				logger.debug("Transfer event " + progressEvent.getEventType() + " transfered bytes was " + progressEvent.getBytesTransferred());
			break;			
		}
	}

}

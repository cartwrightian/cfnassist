package tw.com;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;

public class UploadProgressListener implements ProgressListener {
	private static final Logger logger = LoggerFactory.getLogger(UploadProgressListener.class);
	private static final long CHUNK = 10;
	private long total;
	private long done;
	private long chunk;

	@Override
	public void progressChanged(ProgressEvent progressEvent) {
		try {
			processReceivedEvent(progressEvent);
		}
		catch(Exception exception) {
			logger.error("Error handling S3 progress event ", exception);
		}
	}

	private void processReceivedEvent(ProgressEvent progressEvent) {
		ProgressEventType eventType = progressEvent.getEventType();
		
		long bytesTransferred = progressEvent.getBytesTransferred();
		switch(eventType) {
		case REQUEST_BYTE_TRANSFER_EVENT:
			done += bytesTransferred;
			chunk -= bytesTransferred;
			if (chunk<=0) {
				logger.info(String.format("Sent %s of %s bytes", done, total));
				chunk = total / CHUNK;
			}	
			break;
		case TRANSFER_COMPLETED_EVENT:
				logger.info("Transfer finished");
			break;
		case TRANSFER_FAILED_EVENT:
				logger.error("Transfer failed");
			break;
		case TRANSFER_STARTED_EVENT:
				done = 0;
				logger.info("Transfer started");
			break;
		case REQUEST_CONTENT_LENGTH_EVENT:
			total = progressEvent.getBytes();
			chunk = total / CHUNK;
			logger.info("Length is " + progressEvent.getBytes());
			break;
		case CLIENT_REQUEST_STARTED_EVENT:
		case HTTP_REQUEST_STARTED_EVENT:
		case HTTP_REQUEST_COMPLETED_EVENT:
		case HTTP_RESPONSE_STARTED_EVENT:
		case HTTP_RESPONSE_COMPLETED_EVENT:
		case CLIENT_REQUEST_SUCCESS_EVENT:
				// no-op
			break;
		default:
				logger.debug("Transfer event " + progressEvent.getEventType() + " transfered bytes was " + bytesTransferred);
			break;			
		}
	}

}

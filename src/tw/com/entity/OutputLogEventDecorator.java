package tw.com.entity;

import com.amazonaws.services.logs.model.OutputLogEvent;

public class OutputLogEventDecorator {

    private final OutputLogEvent outputLogEvent;
    private final String groupName;
    private final String streamName;

    public OutputLogEventDecorator(OutputLogEvent outputLogEvent, String groupName, String streamName) {
        this.outputLogEvent = outputLogEvent;
        this.groupName = groupName;
        this.streamName = streamName;
    }

    public long getTimestamp() {
        return outputLogEvent.getTimestamp();
    }

    public String getMessage() {
        return outputLogEvent.getMessage();
    }

    public String getGroupName() {
        return groupName;
    }
}

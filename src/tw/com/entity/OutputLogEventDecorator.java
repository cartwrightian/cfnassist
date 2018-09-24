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

    public String getGroupName() {
        return groupName;
    }
    public String toString() {
        return String.format("%s %s", groupName, outputLogEvent.getMessage());
    }
}

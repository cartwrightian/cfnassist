package tw.com.entity;


import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;

public class OutputLogEventDecorator implements Comparable<OutputLogEventDecorator> {

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
        return String.format("%s %s", streamName, outputLogEvent.message());
    }

    public Long getTimestamp() {
        return outputLogEvent.timestamp();
    }

    @Override
    public int compareTo(OutputLogEventDecorator o) {
        return getTimestamp().compareTo(o.getTimestamp());
    }
}

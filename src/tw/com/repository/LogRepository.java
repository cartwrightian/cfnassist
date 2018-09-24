package tw.com.repository;

import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.OutputLogEvent;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.entity.OutputLogEventDecorator;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;
import tw.com.providers.ProvidesNow;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class LogRepository {
    private static final Logger logger = LoggerFactory.getLogger(LogRepository.class);

    private final LogClient logClient;
    private final ProvidesNow providesNow;

    List<String> required = Arrays.asList(new String[] {AwsFacade.ENVIRONMENT_TAG, AwsFacade.PROJECT_TAG});

    public LogRepository(LogClient logClient, ProvidesNow providesNow) {
        this.logClient = logClient;
        this.providesNow = providesNow;
    }

    public List<String> logGroupsFor(ProjectAndEnv projectAndEnv) {
        Map<String, Map<String, String>> groupsWithTags = logClient.getGroupsWithTags();
        List<String> matched = new LinkedList<>();

        groupsWithTags.forEach((name, tags) -> {
            if (tags.keySet().containsAll(required)) {
                if (tags.get(AwsFacade.ENVIRONMENT_TAG).equals(projectAndEnv.getEnv()) &&
                        tags.get(AwsFacade.PROJECT_TAG).equals(projectAndEnv.getProject())) {
                    matched.add(name);
                }
            }
        });
        logger.info(format("Matched %s out of %s groups to %s", matched.size(), groupsWithTags.size(), projectAndEnv));
        return matched;
    }

    public void removeOldStreamsFor(String groupName, Duration duration) {
        DateTime timestamp = providesNow.getNow();
        long durationInMillis = duration.toMillis();
        long when = timestamp.getMillis()- durationInMillis;

        logger.info(format("Remove streams from group %s if older than %s (%s)", groupName,
                timestamp.minus(durationInMillis), when));

        List<LogStream> streams = logClient.getStreamsFor(groupName);

        streams.stream().
                filter(logStream -> (logStream.getLastEventTimestamp()<when))
                .forEach(oldStream -> {
                    DateTime last = new DateTime(oldStream.getLastEventTimestamp());
                    String streamName = oldStream.getLogStreamName();
                    logger.info(format("Deleting stream %s from group %s, last event was %s", streamName, groupName, last));
                    logClient.deleteLogStream(groupName, streamName);
        });
    }

    public void tagCloudWatchLog(ProjectAndEnv projectAndEnv, String groupToTag) {
        List<String> currnet = logGroupsFor(projectAndEnv);
        if (currnet.contains(groupToTag)) {
            logger.warn(format("Group %s is already tagged for %s", groupToTag, projectAndEnv));
            return;
        }
        logClient.tagGroupFor(projectAndEnv, groupToTag);
    }

    public Stream<String> fetchLogs(ProjectAndEnv projectAndEnv, Duration duration) {
        DateTime timestamp = providesNow.getNow();
        long durationInMillis = duration.toMillis();
        long when = timestamp.getMillis()- durationInMillis;
        logger.info(format("Fetching all log entries for %s in %s", projectAndEnv,duration));

        // TODO expose the streams and do a time ordered merge
        List<String> groupNames = this.logGroupsFor(projectAndEnv);
        if (groupNames.isEmpty()) {
            logger.info("No matching group streams found");
            return Stream.empty();
        }

        logger.info("Matched groups "+groupNames);
        List<Stream<OutputLogEventDecorator>> groupSteams = new LinkedList<>();
        groupNames.forEach(group -> {
            List<LogStream> awsLogStreams = logClient.getStreamsFor(group);
            List<String> streamNames = awsLogStreams.stream().
                    filter(stream -> stream.getLastEventTimestamp()>=when).
                    map(stream -> stream.getLogStreamName()).collect(Collectors.toList());
            logger.info(format("Got %s streams with events in scope", streamNames.size()));
            List<Stream<OutputLogEventDecorator>> fetchLogs = logClient.fetchLogs(group, streamNames, when);
            groupSteams.addAll(fetchLogs);
        });

        if (groupSteams.isEmpty()) {
            logger.info("No group streams found");
            return Stream.empty();
        }

        logger.info("Consolidating group streams");
        // TODO put into the about loop as we want to know group name? Or create decorator for OutputLogEvent
        // that holds the group name and stream name
        // TODO likely need to expose individual streams from fetchLogs to allow time ordering accross groups
        Stream<String> consolidated = mapStream(groupSteams.get(0));
        for (int i = 1; i < groupSteams.size(); i++) {
            consolidated = Stream.concat(consolidated,mapStream(groupSteams.get(i)));
        }
        return consolidated;
    }

    private Stream<String> mapStream(Stream<OutputLogEventDecorator> outputLogEventStream) {
        return outputLogEventStream.map(entry ->  entry.toString());
    }
}

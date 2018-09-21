package tw.com.repository;

import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.OutputLogEvent;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
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

        streams.stream().filter(logStream -> (logStream.getLastEventTimestamp()<when))
                .forEach(oldStream -> {
                    DateTime last = new DateTime(oldStream.getLastEventTimestamp());
                    logger.info(format("Deleting stream %s from group %s, last event was %s", oldStream.getLogStreamName(),
                            groupName, last));
                    logClient.deleteLogStream(groupName, oldStream.getLogStreamName());
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

        // TODO expose the streams and do a time ordered merge

        List<String> groupNames = this.logGroupsFor(projectAndEnv);
        if (groupNames.isEmpty()) {
            return Stream.empty();
        }

        List<Stream<OutputLogEvent>> groupSteams = new LinkedList<>();
        groupNames.forEach(group -> {
            List<LogStream> logStreams = logClient.getStreamsFor(group);
            List<String> streamNames = logStreams.stream().map(s -> s.getLogStreamName()).collect(Collectors.toList());
            groupSteams.add(logClient.fetchLogs(group,streamNames,  when));
        });

        if (groupSteams.isEmpty()) {
            return Stream.empty();
        }

        // TODO likely need to expose individual streams from fetchLogs to allow time ordering accross groups
        Stream<String> consolidated = mapStream(groupNames.get(0), groupSteams.get(0));
        for (int i = 1; i < groupSteams.size(); i++) {
            consolidated = Stream.concat(consolidated,mapStream(groupNames.get(i),groupSteams.get(i)));
        }
        return consolidated;
    }

    private Stream<String> mapStream(String groupName, Stream<OutputLogEvent> outputLogEventStream) {
        return outputLogEventStream.
                map(entry -> {
                    DateTime timestamp = new DateTime((entry.getTimestamp()));
                    return String.format("%s %s %s", groupName, timestamp, entry.getMessage());
                });
    }
}

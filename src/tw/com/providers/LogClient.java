package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import tw.com.AwsFacade;
import tw.com.entity.OutputLogEventDecorator;
import tw.com.entity.ProjectAndEnv;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.ORDERED;

public class LogClient {
    private static final Logger logger = LoggerFactory.getLogger(LogClient.class);
    private final CloudWatchLogsClient theClient;

    public LogClient(CloudWatchLogsClient theClient) {
        this.theClient = theClient;
    }

    public Map<String, Map<String, String>> getGroupsWithTags() {
        Map<String, Map<String,String>> groupsWithTags = new HashMap<>(); // GroupName -> Tags for grouop

        List<LogGroup> groups = getLogGroups();
        logger.info(format("Found %s groups", groups.size()));
        groups.forEach(group->{
            String logGroupName = group.logGroupName();
            ListTagsLogGroupRequest request = ListTagsLogGroupRequest.builder().logGroupName(logGroupName).build();
            ListTagsLogGroupResponse tagResult = theClient.listTagsLogGroup(request);
            Map<String, String> resultTags = tagResult.tags();

            logger.info(format("Group name: %s has tags '%s'", logGroupName, resultTags));
            Map<String, String> tags = resultTags; // TAG -> Value
            groupsWithTags.put(logGroupName, tags);
        });

        return groupsWithTags;
    }

    public List<LogStream> getStreamsFor(String groupName, long when) {
        String initialPagingToken = "";
        return getStreamsFor(groupName, initialPagingToken, when);
    }

    private List<LogStream> getStreamsFor(String groupName, String token, long when) {
        List<LogStream> streamsForGroup = new LinkedList<>();

        DescribeLogStreamsRequest.Builder requestBuilder = DescribeLogStreamsRequest.builder().
                logGroupName(groupName).
                orderBy(OrderBy.LAST_EVENT_TIME).
                // withLimit(200). // defauls to 50 and causes error if set higher..
                descending(true); // newest first

        if (!token.isEmpty()) {
            requestBuilder.nextToken(token);
        }
        DescribeLogStreamsResponse describeResult = theClient.describeLogStreams(requestBuilder.build());
        String nextToken = describeResult.nextToken();

        List<LogStream> logStreams = describeResult.logStreams();
        logger.info(format("Got %s log streams for group %s", logStreams.size(), groupName));
        logger.debug("Next token was: " + nextToken);
        boolean tooOld = false;
        for (LogStream stream : logStreams) {
            String logStreamName = stream.logStreamName();
            logger.debug(format("Processing stream '%s' for group '%s", logStreamName, groupName));
            Long firstEvent = stream.firstEventTimestamp();
            Long lastEvent = stream.lastEventTimestamp();

            if (firstEvent==null) {
                logger.warn(format("Group '%s' stream '%s' has null start event, assume in scope", groupName, logStreamName));
                firstEvent = when + 1;
            }
            if (lastEvent==null) {
                logger.warn(format("Group '%s' stream '%s' has null last event, assume in scope", groupName, logStreamName));
                lastEvent = when + 1;
            }

            if (firstEvent>when || lastEvent>when) {
                logger.info(format("Adding stream: %s", logStreamName));
                streamsForGroup.add(stream);
            } else {
                logger.info(format("Log stream '%s' is too old", logStreamName));
                tooOld = true;
                break;
            }
        }

        // TODO optimise based on ordering of the streams above?
        if (! ((nextToken==null) || token.equals(nextToken) || tooOld)) {
            // recursive call with nextToken, needed as with large number of streams
            streamsForGroup.addAll(getStreamsFor(groupName, nextToken, when));
        }
        logger.info(format("Added %s streams for group %s", streamsForGroup.size(), groupName));
        return streamsForGroup;
    }

    public void deleteLogStream(String groupdName, String streamName) {
        DeleteLogStreamRequest request = DeleteLogStreamRequest.builder().
                logGroupName(groupdName).
                logStreamName(streamName).build();
        DeleteLogStreamResponse result = theClient.deleteLogStream(request);
        logger.info(format("Deleted %s for group %s result was %s", streamName, groupdName, result));
    }

    private List<LogGroup> getLogGroups() {
        DescribeLogGroupsResponse result = theClient.describeLogGroups();
        return result.logGroups();
    }

    public void tagGroupFor(ProjectAndEnv projectAndEnv, String groupToTag) {
        logger.info(format("Tag log group %s with %s", groupToTag, projectAndEnv));

        Map<String, String> tags = new HashMap<>();
        tags.put(AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv());
        tags.put(AwsFacade.PROJECT_TAG, projectAndEnv.getProject());
        TagLogGroupRequest.Builder requestBuilder = TagLogGroupRequest.builder().
                logGroupName(groupToTag).tags(tags);

        theClient.tagLogGroup(requestBuilder.build());
    }

    public List<Stream<OutputLogEventDecorator>> fetchLogs(String groupName, List<String> streamNames, Long endEpoch) {
        List<Stream<OutputLogEventDecorator>> outputStreams = new LinkedList<>();

        if (streamNames.isEmpty()) {
            return outputStreams;
        }

        streamNames.forEach(streamName -> {
            logger.info(format("Fetching output streams for '%s'", streamName));
            TokenStrategy currentToken = new TokenStrategy();
            Iterable<OutputLogEventDecorator> iterator = new LogIterator(groupName, streamName, endEpoch, currentToken);
            Spliterator<OutputLogEventDecorator> spliterator = Spliterators.spliteratorUnknownSize(iterator.iterator(), IMMUTABLE | ORDERED );

            outputStreams.add(StreamSupport.stream(spliterator, false));
        });

        return outputStreams;
    }

    // control shared token or token per stream
    private class TokenStrategy {
            private String token = "";

            public void set(GetLogEventsResponse result) {
                this.token = result.nextForwardToken();
            }

            public boolean isEmpty() {
                return token.isEmpty();
            }

            public String get() {
                return token;
            }

            public String toString() {
                return "token:"+ token;
            }

        boolean tokenMatch(GetLogEventsResponse result) {
            return token.equals(result.nextForwardToken());
        }
    }

    // class to facade the AWS API 'paging' behavior for a single log stream
    private class LogIterator implements Iterable<OutputLogEventDecorator>  {
        private final String groupName;
        private final String streamName;
        private final Long endEpoch;

        private Iterator<OutputLogEvent> inProgress;
        private TokenStrategy currentToken;

        private LogIterator(String groupName, String streamName, Long endEpoch, TokenStrategy currentToken) {
            this.groupName = groupName;
            this.streamName = streamName;
            this.endEpoch = endEpoch;
            this.currentToken = currentToken;
            // initially empty
            inProgress = new LinkedList<OutputLogEvent>().iterator();
        }

        @Override
        public Iterator<OutputLogEventDecorator> iterator() {
            return new Iterator<OutputLogEventDecorator>() {
                @Override
                public boolean hasNext() {
                    if (inProgress.hasNext()) {
                        return true;
                    }
                   return loadNextResult();
                }

                @Override
                public OutputLogEventDecorator next() {
                    if (inProgress.hasNext()) {
                        return new OutputLogEventDecorator(inProgress.next(), groupName, streamName);
                    }
                    throw new NoSuchElementException();
                }
            };
        }

        private boolean loadNextResult() {
            GetLogEventsResponse nextResult = getLogEvents();
            if (nextResult.events().isEmpty()) {
                return false;
            }
            if (currentToken.tokenMatch(nextResult)) {
                logger.debug("LogIterator: Next token matches previous, no more results for stream " + streamName);
                return false;
            }
            currentToken.set(nextResult);
            inProgress = nextResult.events().iterator();
            return true;
        }

        private GetLogEventsResponse getLogEvents() {
            logger.debug(format("LogIterator: Getting events for %s stream %s and epoch %s", groupName, streamName, endEpoch));
            GetLogEventsRequest.Builder requestBuilder = GetLogEventsRequest.builder().
                    logGroupName(groupName).
                    logStreamName(streamName).
                    startTime(endEpoch). // do we need this if have already selected stream(name)s that are in scope?
                    startFromHead(true); // earlier events come first

            if (!currentToken.isEmpty()) {
                logger.debug(format("LogIterator: Setting nextToken on stream %s request to %s", streamName, currentToken));
                requestBuilder.nextToken(currentToken.get());
            }

            GetLogEventsResponse currentResult = theClient.getLogEvents(requestBuilder.build());
            logger.debug(format("LogIterator: Got %s entries for stream %s", currentResult.events().size(), streamName));
            logger.debug(format("LogIterator: Got nextToken on stream %s token: %s", streamName, currentResult.nextForwardToken()));
            return currentResult;
        }

    }
}

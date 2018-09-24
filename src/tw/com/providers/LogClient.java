package tw.com.providers;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final AWSLogs theClient;

    public LogClient(AWSLogs theClient) {
        this.theClient = theClient;
    }

    public Map<String, Map<String, String>> getGroupsWithTags() {
        Map<String, Map<String,String>> groupsWithTags = new HashMap<>(); // GroupName -> Tags for grouop

        List<LogGroup> groups = getLogGroups();
        logger.info(format("Found %s groups", groups.size()));
        groups.forEach(group->{
            String logGroupName = group.getLogGroupName();
            logger.info("Group name: " + logGroupName);
            ListTagsLogGroupResult tagResult = theClient.listTagsLogGroup(new ListTagsLogGroupRequest().withLogGroupName(logGroupName));
            Map<String, String> tags = tagResult.getTags(); // TAG -> Value
            groupsWithTags.put(logGroupName, tags);

        });

        return groupsWithTags;
    }

    public List<LogStream> getStreamsFor(String groupName) {
        List<LogStream> accum = new LinkedList<>();
        getStreamsFor(accum, groupName, "");
        return accum;
    }

    public void getStreamsFor(List<LogStream> accum, String groupName, String token) {
        DescribeLogStreamsRequest request = new DescribeLogStreamsRequest().withLogGroupName(groupName);
        if (!token.isEmpty()) {
            request.setNextToken(token);
        }
        DescribeLogStreamsResult describeResult = theClient.describeLogStreams(request);
        String nextToken = describeResult.getNextToken();
        List<LogStream> logStreams = describeResult.getLogStreams();

        logger.info(format("Got %s log streams for group %s and token: %s ", logStreams.size(), groupName, nextToken));
        accum.addAll(logStreams);
        if (!(nextToken==null) || token.equals(nextToken)) {
            getStreamsFor(accum, groupName, nextToken);
        }
    }

    public void deleteLogStream(String groupdName, String streamName) {
        DeleteLogStreamRequest request = new DeleteLogStreamRequest().withLogGroupName(groupdName).withLogStreamName(streamName);
        DeleteLogStreamResult result = theClient.deleteLogStream(request);
        logger.info(format("Deleted %s for group %s result was %s", streamName, groupdName, result));
    }

    private List<LogGroup> getLogGroups() {
        DescribeLogGroupsResult result = theClient.describeLogGroups();
        return result.getLogGroups();
    }

    public void tagGroupFor(ProjectAndEnv projectAndEnv, String groupToTag) {
        logger.info(format("Tag log group %s with %s", groupToTag, projectAndEnv));

        TagLogGroupRequest request = new TagLogGroupRequest().withLogGroupName(groupToTag);
        request.addTagsEntry(AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv());
        request.addTagsEntry(AwsFacade.PROJECT_TAG, projectAndEnv.getProject());
        theClient.tagLogGroup(request);
    }

    public List<Stream<OutputLogEventDecorator>> fetchLogs(String groupName, List<String> streamNames, Long endEpoch) {
        List<Stream<OutputLogEventDecorator>> outputStreams = new LinkedList<>();

        if (streamNames.isEmpty()) {
            return outputStreams;
        }

        streamNames.forEach(streamName -> {
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

            public void set(GetLogEventsResult result) {
                this.token = result.getNextForwardToken();
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

        public boolean tokenMatch(GetLogEventsResult result) {
            return token.equals(result.getNextForwardToken());
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
            GetLogEventsResult nextResult = getLogEvents();
            if (nextResult.getEvents().isEmpty()) {
                return false;
            }
            if (currentToken.tokenMatch(nextResult)) {
                logger.info("Next token matches previous, no more results for stream " + streamName);
                return false;
            }
            currentToken.set(nextResult);
            inProgress = nextResult.getEvents().iterator();
            return true;
        }

        private GetLogEventsResult getLogEvents() {
            logger.info(format("Getting events for %s stream %s and epoch %s", groupName, streamName, endEpoch));
            GetLogEventsRequest request = new GetLogEventsRequest().
                    withLogGroupName(groupName).
                    withLogStreamName(streamName).withStartTime(endEpoch);

            if (!currentToken.isEmpty()) {
                logger.info(format("Setting nextToken on stream %s request to %s", streamName, currentToken));
                request.setNextToken(currentToken.get());
            }

            GetLogEventsResult currentResult = theClient.getLogEvents(request);
            logger.info(format("Got %s entries for stream %s", currentResult.getEvents().size(), streamName));
            logger.info(format("Got nextToken on stream %s token: %s", streamName, currentResult.getNextForwardToken()));
            return currentResult;
        }

    }
}

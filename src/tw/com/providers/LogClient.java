package tw.com.providers;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
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
        logger.info("Get log streams for group " + groupName);
        DescribeLogStreamsRequest request = new DescribeLogStreamsRequest().withLogGroupName(groupName);
        DescribeLogStreamsResult result = theClient.describeLogStreams(request);
        return result.getLogStreams();
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

    public Stream<OutputLogEvent> fetchLogs(String groupName, List<String> streamNames, Long endEpoch) {
        if (streamNames.isEmpty()) {
            return Stream.empty();
        }
        List<Stream<OutputLogEvent>> outputStreams = new LinkedList<>();

        streamNames.forEach(streamName -> {
            Iterable<OutputLogEvent> iterator = new LogIterator(groupName, streamName, endEpoch);
            Spliterator<OutputLogEvent> spliterator = Spliterators.spliteratorUnknownSize(iterator.iterator(), IMMUTABLE | ORDERED );
            outputStreams.add(StreamSupport.stream(spliterator, false));
        });

        Stream<OutputLogEvent> result = outputStreams.get(0);
        for (int i = 1; i < outputStreams.size(); i++) {
            result = Stream.concat(result,outputStreams.get(i));
        }
        return result;
    }

    // class to facade the AWS API 'paging' behavior for a single log stream
    private class LogIterator implements Iterable<OutputLogEvent>  {
        private final String groupName;
        private final String streamName;
        private final Long endEpoch;

        private Iterator<OutputLogEvent> inProgress;
        private String currentToken;

        private LogIterator(String groupName, String streamName, Long endEpoch) {
            this.groupName = groupName;
            this.streamName = streamName;
            this.endEpoch = endEpoch;
            currentToken = "";
            // initial load
            loadNextResult();
        }

        @Override
        public Iterator<OutputLogEvent> iterator() {
            return new Iterator<OutputLogEvent>() {
                @Override
                public boolean hasNext() {
                    if (inProgress.hasNext()) {
                        return true;
                    }
                   return loadNextResult();
                }

                @Override
                public OutputLogEvent next() {
                    if (inProgress.hasNext()) {
                        return inProgress.next();
                    }
                    throw new NoSuchElementException();
                }
            };
        }

        private boolean loadNextResult() {
            GetLogEventsResult nextResult = getLogEventsUnFiltered();
            if (currentToken.equals(nextResult.getNextForwardToken())) {
                logger.info("Next token matches previous, no more results for stream " + streamName);
                return false;
            }
            currentToken = nextResult.getNextForwardToken();
            inProgress = nextResult.getEvents().iterator();
            return true;
        }

        private GetLogEventsResult getLogEventsUnFiltered() {
            logger.info(format("Getting events for %s stream %s and epoch %s", groupName, streamName, endEpoch));
            GetLogEventsRequest request = new GetLogEventsRequest().
                    withLogGroupName(groupName).
                    withLogStreamName(streamName).
                    withStartTime(endEpoch);

            if (!currentToken.isEmpty()) {
                logger.info(format("Setting nextToken on stream %s request to %s", streamName, currentToken));
                request.setNextToken(currentToken);
            }

            GetLogEventsResult currentResult = theClient.getLogEvents(request);
            logger.info(format("Got nextToken on stream %s request as %s", streamName, currentToken));
            return currentResult;
        }

    }
}

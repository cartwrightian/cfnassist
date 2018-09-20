package tw.com.providers;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class LogClient {
    private static final Logger logger = LoggerFactory.getLogger(LogClient.class);
    private final AWSLogs theClient;

    public LogClient(AWSLogs theClient) {
        this.theClient = theClient;
    }

    public Map<String, Map<String, String>> getGroupsWithTags() {
        Map<String, Map<String,String>> groupsWithTags = new HashMap<>(); // GroupName -> Tags for grouop

        List<LogGroup> groups = getLogGroups();
        groups.forEach(group->{
            ListTagsLogGroupResult tagResult = theClient.listTagsLogGroup(new ListTagsLogGroupRequest().withLogGroupName(group.getLogGroupName()));

            Map<String, String> tags = tagResult.getTags(); // TAG -> Value
            groupsWithTags.put(group.getLogGroupName(), tags);

        });
        logger.info(format("Found %s groups matching out of %s in total", groupsWithTags.size(), groups.size()));

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
}

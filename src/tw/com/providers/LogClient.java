package tw.com.providers;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;

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
}

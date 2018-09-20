package tw.com.integration;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.junit.*;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.LogClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class TestLogClient {

    public static final String TEST_LOG_GROUP = "testLogGroup";
    private static AWSLogs awsLogs;
    private LogClient logClient;

    @BeforeClass
    public static void beforeAnyTestsRun() {
        awsLogs = EnvironmentSetupForTests.createAWSLogsClient();
        Map<String, String> tags = new HashMap<>();
        tags.put("TESTA", "valuea");
        tags.put("TESTB", "42");
        awsLogs.createLogGroup(new CreateLogGroupRequest().withLogGroupName(TEST_LOG_GROUP).withTags(tags));
    }

    @AfterClass
    public static void afterAllTestRun() {
        awsLogs.deleteLogGroup(new DeleteLogGroupRequest().withLogGroupName(TEST_LOG_GROUP));
    }

    @Before
    public void beforeEachTestRuns() {
        logClient = new LogClient(awsLogs);
    }

    @Test
    public void shouldGetLogGroups() {

        Map<String, Map<String, String>> result = logClient.getGroupsWithTags();

        assertTrue(result.containsKey(TEST_LOG_GROUP));
        Map<String, String> tagsForGroup = result.get(TEST_LOG_GROUP);
        assertEquals(2, tagsForGroup.size());
        assertTrue(tagsForGroup.containsKey("TESTA"));
        assertTrue(tagsForGroup.containsKey("TESTB"));

        assertEquals("valuea", tagsForGroup.get("TESTA"));
        assertEquals("42", tagsForGroup.get("TESTB"));
    }

    @Test
    public void shouldGetAndDeleteLogStream() {
        String streamName = "testStreamName";
        awsLogs.createLogStream(new CreateLogStreamRequest().
                withLogGroupName(TEST_LOG_GROUP).
                withLogStreamName(streamName));

        List<LogStream> streams = logClient.getStreamsFor(TEST_LOG_GROUP);
        assertEquals(1, streams.size());
        assertEquals(streamName, streams.get(0).getLogStreamName());
        logClient.deleteLogStream(TEST_LOG_GROUP, streamName);

        DescribeLogStreamsResult actual = awsLogs.describeLogStreams(new DescribeLogStreamsRequest().withLogGroupName(TEST_LOG_GROUP));
        List<String> names = actual.getLogStreams().stream().map(stream -> stream.getLogStreamName()).collect(Collectors.toList());
        assertFalse(names.contains(streamName));
    }
}

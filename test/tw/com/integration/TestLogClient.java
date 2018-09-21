package tw.com.integration;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.joda.time.DateTime;
import org.junit.*;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        try {
            awsLogs.createLogGroup(new CreateLogGroupRequest().withLogGroupName(TEST_LOG_GROUP).withTags(tags));
        }
        catch (ResourceAlreadyExistsException alreadyPresent) {
            // no op
        }
    }

    @AfterClass
    public static void afterAllTestRun() {
        awsLogs.deleteLogGroup(new DeleteLogGroupRequest().withLogGroupName(TEST_LOG_GROUP));
        awsLogs.shutdown();
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

    @Test
    public void shouldTagAGroupWithProjectAndEnv() {
        ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

        logClient.tagGroupFor(projectAndEnv, TEST_LOG_GROUP);

        ListTagsLogGroupResult actual = awsLogs.listTagsLogGroup(new ListTagsLogGroupRequest().withLogGroupName(TEST_LOG_GROUP));

        Map<String, String> result = actual.getTags();
        assertTrue(result.containsKey(AwsFacade.PROJECT_TAG));
        assertTrue(result.containsKey(AwsFacade.ENVIRONMENT_TAG));

        assertEquals(projectAndEnv.getProject(), result.get(AwsFacade.PROJECT_TAG));
        assertEquals(projectAndEnv.getEnv(), result.get(AwsFacade.ENVIRONMENT_TAG));
    }

    @Test
    public void SPIKE_shouldStreamLogEntries() throws InterruptedException {
        // multiple streams to test out interleaving
        List<String> streamNames = Arrays.asList("streamA", "streamB", "streamC");
        int numberOfEventPerStream = 2000;

        streamNames.forEach(streamName -> {
            awsLogs.createLogStream(new CreateLogStreamRequest().
                    withLogGroupName(TEST_LOG_GROUP).
                    withLogStreamName(streamName));
        });

        DateTime beginInsert = DateTime.now();
        int expectedSize = streamNames.size() * numberOfEventPerStream;

        streamNames.forEach(streamName -> {
            // responses split by token when size of one response >1MB
            PutLogEventsRequest putEventsRequest = new PutLogEventsRequest();
            Collection<InputLogEvent> events = createTestLogEvents(numberOfEventPerStream, beginInsert.getMillis());
            putEventsRequest.withLogGroupName(TEST_LOG_GROUP).withLogStreamName(streamName).withLogEvents(events);
            awsLogs.putLogEvents(putEventsRequest);
        });

        Thread.sleep(6000);
        // inserted log events are not immediately available for consumption

        Long epoch = beginInsert.getMillis();
        Stream<OutputLogEvent> resultStream = logClient.fetchLogs(TEST_LOG_GROUP, streamNames, epoch);
        List<OutputLogEvent> result = resultStream.collect(Collectors.toList());

        // no asserts until after tidy up streams
        streamNames.forEach(streamName -> awsLogs.deleteLogStream(new DeleteLogStreamRequest().
                withLogGroupName(TEST_LOG_GROUP).
                withLogStreamName(streamName)));

        assertEquals(expectedSize, result.size());

    }

    private Collection<InputLogEvent> createTestLogEvents(int number, Long epoch) {
        List<InputLogEvent> events = new LinkedList<>();
        for (int i = 0; i < number; i++) {
            InputLogEvent logEvent = new InputLogEvent().withMessage("This is log message number "+i).
                    withTimestamp(epoch);
            events.add(logEvent);
        }
        return events;
    }
}

package tw.com.integration;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.*;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.OutputLogEventDecorator;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;
import tw.com.providers.ProvidesNow;
import tw.com.providers.SavesFile;
import tw.com.repository.LogRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static junit.framework.TestCase.*;

public class TestLogClientAndRepository {

    private static final String TEST_LOG_GROUP = "testLogGroup";
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

    @After
    public void afterEachTestRuns() {
        // delete all streams for group TEST_LOG_GROUP
        DescribeLogStreamsResult existing = awsLogs.describeLogStreams(new DescribeLogStreamsRequest().withLogGroupName(TEST_LOG_GROUP));
        List<String> streamNames = existing.getLogStreams().stream().map(LogStream::getLogStreamName).collect(Collectors.toList());
        streamNames.forEach(streamName -> awsLogs.deleteLogStream(new DeleteLogStreamRequest().
                withLogGroupName(TEST_LOG_GROUP).
                withLogStreamName(streamName)));
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

        List<LogStream> streams = logClient.getStreamsFor(TEST_LOG_GROUP, Long.MIN_VALUE); // MIN_VALUE=all
        assertEquals(1, streams.size());
        assertEquals(streamName, streams.get(0).getLogStreamName());
        logClient.deleteLogStream(TEST_LOG_GROUP, streamName);

        DescribeLogStreamsResult actual = awsLogs.describeLogStreams(new DescribeLogStreamsRequest().withLogGroupName(TEST_LOG_GROUP));
        List<String> names = actual.getLogStreams().stream().map(LogStream::getLogStreamName).collect(Collectors.toList());
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
    public void shouldTestWithLogStreams() throws InterruptedException {
        //
        // For large numbers of log entires hard to anticipate how long it takes from end of API call to the
        // log events being available, so for now restrict number even if this does test out the paging behaviours
        //
        List<String> streamNames = Arrays.asList("streamA", "streamB", "streamC");
        int numberOfEventsPerUpload = 200;
        int numberOfUploads = 6;

        DescribeLogStreamsResult existing = awsLogs.describeLogStreams(new DescribeLogStreamsRequest().withLogGroupName(TEST_LOG_GROUP));
        List<String> present = existing.getLogStreams().stream().map(LogStream::getLogStreamName).collect(Collectors.toList());

        streamNames.forEach(streamName -> {
                if (!present.contains(streamName))
                awsLogs.createLogStream(new CreateLogStreamRequest().
                withLogGroupName(TEST_LOG_GROUP).
                withLogStreamName(streamName));}
                );

        DateTime beginInsert = DateTime.now();

        uploadTestEvents(streamNames, numberOfEventsPerUpload, numberOfUploads, beginInsert);

        Thread.sleep(2000); // inserted log events are not immediately available for consumption

        Long epoch = beginInsert.getMillis();
        List<Stream<OutputLogEventDecorator>> resultStream = logClient.fetchLogs(TEST_LOG_GROUP, streamNames, epoch);

        assertEquals(3, resultStream.size());

        int expectedSize = numberOfEventsPerUpload * numberOfUploads;
        assertEquals(expectedSize, resultStream.get(0).count());
        assertEquals(expectedSize, resultStream.get(1).count());
        assertEquals(expectedSize, resultStream.get(2).count());
    }

    @Test
    public void shouldFetchLogResultsViaLogRepository() throws IOException, InterruptedException {
        DateTime timeStamp = DateTime.now();

        ProvidesNow providesNow = () -> timeStamp;
        SavesFile savesFile = new SavesFile();
        LogRepository logRepository = new LogRepository(logClient, providesNow, savesFile);

        ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

        logClient.tagGroupFor(projectAndEnv, TEST_LOG_GROUP);
        awsLogs.createLogStream(new CreateLogStreamRequest().
                withLogGroupName(TEST_LOG_GROUP).
                withLogStreamName("repoTestStream"));

        Path expectedFilename = Paths.get(String.format("%s_%s.log",TEST_LOG_GROUP,
                timeStamp.toString(ISODateTimeFormat.basicDateTime())));
        Files.deleteIfExists(expectedFilename);

        // TODO sleep after this if upload not completed
        uploadTestEvents(Arrays.asList("repoTestStream"), 200, 10, timeStamp);

        Thread.sleep(2000); // inserted log events are not immediately available for consumption

        List<Path> filenames = logRepository.fetchLogs(projectAndEnv, Duration.ofDays(1));

        assertEquals(1, filenames.size());
        Path result = filenames.get(0);

        boolean present = Files.exists(expectedFilename);
        List<String> lines = Files.readAllLines(result);

        Files.deleteIfExists(result); // in case mismatch
        Files.deleteIfExists(expectedFilename);

        assertEquals(expectedFilename.toAbsolutePath().toString(), result.toAbsolutePath().toString());
        assertTrue(present);
        assertEquals(2000, lines.size());
    }

    private void uploadTestEvents(List<String> streamNames, int numberOfEventsPerUpload, int numberOfUploads, DateTime beginInsert) {
        List<String> nextSeqToken = new LinkedList<>();
        for (int i = 0; i <numberOfUploads; i++) {
            streamNames.forEach(streamName -> {
                // responses split by token when size of one response >1MB
                PutLogEventsRequest putEventsRequest = new PutLogEventsRequest();
                Collection<InputLogEvent> events = createTestLogEvents(numberOfEventsPerUpload, beginInsert.getMillis());
                putEventsRequest.withLogGroupName(TEST_LOG_GROUP).withLogStreamName(streamName).withLogEvents(events);
                nextSeqToken.forEach(token -> putEventsRequest.setSequenceToken(token));
                try {
                    awsLogs.putLogEvents(putEventsRequest);
                }
                catch(DataAlreadyAcceptedException alreadyExcepted) {
                    String token = alreadyExcepted.getExpectedSequenceToken();
                    putEventsRequest.setSequenceToken(token);
                    awsLogs.putLogEvents(putEventsRequest);

                    nextSeqToken.clear();
                    nextSeqToken.add(token);
                }
                catch (InvalidSequenceTokenException invalidToken) {
                    String token = invalidToken.getExpectedSequenceToken();
                    putEventsRequest.setSequenceToken(token);
                    awsLogs.putLogEvents(putEventsRequest);

                    nextSeqToken.clear();
                    nextSeqToken.add(token);
                }

            });
        }
    }

    private Collection<InputLogEvent> createTestLogEvents(int number, Long epoch) {
        List<InputLogEvent> events = new LinkedList<>();
        StringBuilder builder = new StringBuilder();
        builder.append("XXXXXXXXX");
        for (int m = 0; m < 5; m++) {
            builder.append(builder.toString());
        }
        for (int i = 0; i < number; i++) {
            InputLogEvent logEvent = new InputLogEvent().
                    withMessage("This is log message number "+i+" "+builder.toString()).
                    withTimestamp(epoch);
            events.add(logEvent);
        }
        return events;
    }
}

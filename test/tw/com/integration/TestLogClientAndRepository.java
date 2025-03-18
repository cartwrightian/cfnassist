package tw.com.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
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
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static junit.framework.TestCase.*;

public class TestLogClientAndRepository {

    private static final String TEST_LOG_GROUP = "testLogGroup";
    private static CloudWatchLogsClient awsLogs;
    private LogClient logClient;

    @BeforeAll
    public static void beforeAnyTestsRun() {
        awsLogs = EnvironmentSetupForTests.createAWSLogsClient();
        Map<String, String> tags = new HashMap<>();
        tags.put("TESTA", "valuea");
        tags.put("TESTB", "42");
        try {
            awsLogs.createLogGroup(CreateLogGroupRequest.builder().logGroupName(TEST_LOG_GROUP).tags(tags).build());
        }
        catch (ResourceAlreadyExistsException alreadyPresent) {
            // no op
        }
    }

    @AfterAll
    public static void afterAllTestRun() {
        awsLogs.deleteLogGroup(DeleteLogGroupRequest.builder().logGroupName(TEST_LOG_GROUP).build());
        //awsLogs.shutdown();
    }

    @BeforeEach
    public void beforeEachTestRuns() {
        logClient = new LogClient(awsLogs);
    }

    @AfterEach
    public void afterEachTestRuns() {
        // delete all streams for group TEST_LOG_GROUP
        DescribeLogStreamsResponse existing = awsLogs.describeLogStreams(DescribeLogStreamsRequest.builder().
                logGroupName(TEST_LOG_GROUP).build());
        List<String> streamNames = existing.logStreams().stream().map(LogStream::logStreamName).collect(Collectors.toList());
        streamNames.forEach(streamName -> awsLogs.deleteLogStream(DeleteLogStreamRequest.builder().
                logGroupName(TEST_LOG_GROUP).
                logStreamName(streamName).build()));
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
        awsLogs.createLogStream(CreateLogStreamRequest.builder().
                logGroupName(TEST_LOG_GROUP).
                logStreamName(streamName).build());

        List<LogStream> streams = logClient.getStreamsFor(TEST_LOG_GROUP, Long.MIN_VALUE); // MIN_VALUE=all
        assertEquals(1, streams.size());
        assertEquals(streamName, streams.get(0).logStreamName());
        logClient.deleteLogStream(TEST_LOG_GROUP, streamName);

        DescribeLogStreamsResponse actual = awsLogs.describeLogStreams(DescribeLogStreamsRequest.builder().logGroupName(TEST_LOG_GROUP).build());
        List<String> names = actual.logStreams().stream().map(LogStream::logStreamName).collect(Collectors.toList());
        assertFalse(names.contains(streamName));
    }

    @Test
    public void shouldTagAGroupWithProjectAndEnv() {
        ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

        logClient.tagGroupFor(projectAndEnv, TEST_LOG_GROUP);

        ListTagsLogGroupResponse actual = awsLogs.listTagsLogGroup(ListTagsLogGroupRequest.builder().logGroupName(TEST_LOG_GROUP).build());

        Map<String, String> result = actual.tags();
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

        DescribeLogStreamsResponse existing = awsLogs.describeLogStreams(DescribeLogStreamsRequest.builder().
                logGroupName(TEST_LOG_GROUP).build());
        List<String> present = existing.logStreams().stream().map(LogStream::logStreamName).collect(Collectors.toList());

        streamNames.forEach(streamName -> {
                if (!present.contains(streamName))
                awsLogs.createLogStream(CreateLogStreamRequest.builder().
                    logGroupName(TEST_LOG_GROUP).
                    logStreamName(streamName).build());}
                );

        ZonedDateTime beginInsert = ZonedDateTime.now(ZoneId.of("UTC"));

        uploadTestEvents(streamNames, numberOfEventsPerUpload, numberOfUploads, beginInsert);

        Thread.sleep(2000); // inserted log events are not immediately available for consumption

        Long epoch = EnvironmentSetupForTests.asMillis(beginInsert);
        List<Stream<OutputLogEventDecorator>> resultStream = logClient.fetchLogs(TEST_LOG_GROUP, streamNames, epoch);

        assertEquals(3, resultStream.size());

        int expectedSize = numberOfEventsPerUpload * numberOfUploads;
        assertEquals(expectedSize, resultStream.get(0).count());
        assertEquals(expectedSize, resultStream.get(1).count());
        assertEquals(expectedSize, resultStream.get(2).count());
    }


    @Test
    public void shouldFetchLogResultsViaLogRepository() throws IOException, InterruptedException {
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneId.of("UTC"));

        ProvidesNow providesNow = () -> timeStamp;
        SavesFile savesFile = new SavesFile();
        LogRepository logRepository = new LogRepository(logClient, providesNow, savesFile);

        ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();

        logClient.tagGroupFor(projectAndEnv, TEST_LOG_GROUP);
        awsLogs.createLogStream(CreateLogStreamRequest.builder().
                logGroupName(TEST_LOG_GROUP).
                logStreamName("repoTestStream").build());

        Path expectedFilename = Paths.get(String.format("%s_%s.log",TEST_LOG_GROUP,
                timeStamp.format(DateTimeFormatter.ISO_DATE_TIME)));
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

    private void uploadTestEvents(List<String> streamNames, int numberOfEventsPerUpload, int numberOfUploads,
                                  ZonedDateTime beginInsert) {
        List<String> nextSeqToken = new LinkedList<>();
        for (int i = 0; i <numberOfUploads; i++) {
            streamNames.forEach(streamName -> {
                // responses split by token when size of one response >1MB
                PutLogEventsRequest.Builder putEventsRequest = PutLogEventsRequest.builder();
                Collection<InputLogEvent> events = createTestLogEvents(numberOfEventsPerUpload,
                        EnvironmentSetupForTests.asMillis(beginInsert));
                putEventsRequest.logGroupName(TEST_LOG_GROUP).logStreamName(streamName).logEvents(events);
                nextSeqToken.forEach(putEventsRequest::sequenceToken);
                try {
                    awsLogs.putLogEvents(putEventsRequest.build());
                }
                catch(DataAlreadyAcceptedException alreadyExcepted) {
                    String token = alreadyExcepted.expectedSequenceToken();
                    putEventsRequest.sequenceToken(token);
                    awsLogs.putLogEvents(putEventsRequest.build());

                    nextSeqToken.clear();
                    nextSeqToken.add(token);
                }
                catch (InvalidSequenceTokenException invalidToken) {
                    String token = invalidToken.expectedSequenceToken();
                    putEventsRequest.sequenceToken(token);
                    awsLogs.putLogEvents(putEventsRequest.build());

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
            InputLogEvent logEvent = InputLogEvent.builder().
                    message("This is log message number "+i+" "+builder.toString()).
                    timestamp(epoch).build();
            events.add(logEvent);
        }
        return events;
    }
}

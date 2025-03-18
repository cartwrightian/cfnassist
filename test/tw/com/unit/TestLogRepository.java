package tw.com.unit;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.OutputLogEventDecorator;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;
import tw.com.providers.SavesFile;
import tw.com.repository.LogRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class TestLogRepository  extends EasyMockSupport {
    private LogClient logClient;
    private ProjectAndEnv projectAndEnv;
    private LogRepository logRepository;
    private ZonedDateTime timestamp;
    private SavesFile savesFile;

    @BeforeEach
    public void beforeEachTestRuns() {
        timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        timestamp = timestamp.minusMinutes(timestamp.getMinute()).minusHours(timestamp.getHour()); // use midnight

        projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        logClient = createStrictMock(LogClient.class);
        savesFile = createStrictMock(SavesFile.class);

        logRepository = new LogRepository(logClient, () -> timestamp, savesFile);
    }

    @Test
    public void shouldFilterGroupsByTags() {
        Map<String, Map<String, String>> groups = new HashMap<>();
        createExistingGroups(groups);
        EasyMock.expect(logClient.getGroupsWithTags()).andReturn(groups);

        replayAll();
        List<String> result = logRepository.logGroupsFor(projectAndEnv);
        verifyAll();

        assertEquals(1, result.size());
        assertTrue(result.contains("groupB"));
    }

    @Test
    public void shouldRemoveOldStreamsForGroupList() {
        int days = 28;

        LogStream streamA = createStream(EnvironmentSetupForTests.asMillis(timestamp.minusDays(20)), "streamA");
        LogStream streamB = createStream(EnvironmentSetupForTests.asMillis(timestamp.minusDays(29)), "streamB");
        LogStream streamC = createStream(EnvironmentSetupForTests.asMillis(timestamp.minusDays(27)), "streamC");
        List<LogStream> streams = Arrays.asList(streamA, streamB, streamC);

        long queryTime = EnvironmentSetupForTests.asMillis(timestamp.minusDays(days));

        EasyMock.expect(logClient.getStreamsFor("groupName", queryTime)).andReturn(streams);
        logClient.deleteLogStream("groupName", "streamB");
        EasyMock.expectLastCall();

        replayAll();
        logRepository.removeOldStreamsFor("groupName", Duration.ofDays(days));
        verifyAll();
    }

    @Test
    public void shouldTagGroupWithEnvAndProject() {
        Map<String, Map<String, String>> groups = new HashMap<>();
        createExistingGroups(groups);
        EasyMock.expect(logClient.getGroupsWithTags()).andReturn(groups);
        logClient.tagGroupFor(projectAndEnv, "groupA");
        EasyMock.expectLastCall();

        replayAll();
        logRepository.tagCloudWatchLog(projectAndEnv, "groupA");
        verifyAll();
    }

    @Test
    public void shouldNotTagIfAlreadyDoneAndMatches() {
        Map<String, Map<String, String>> groups = new HashMap<>();
        createExistingGroups(groups);
        EasyMock.expect(logClient.getGroupsWithTags()).andReturn(groups);

        replayAll();
        logRepository.tagCloudWatchLog(projectAndEnv, "groupB");
        verifyAll();
    }

    @Test
    public void shouldFetchLogs() {
        List<LogStream> logStreams = new LinkedList<>();

        String groupName = "groupB";
        List<String> streamNames = Arrays.asList("streamA", "streamB");

        int days = 42;
        long queryTime = EnvironmentSetupForTests.asMillis(timestamp.minusDays(days));
        long eventTime = EnvironmentSetupForTests.asMillis(timestamp.minusDays(days-1));

        Path expectedPath = Paths.get(format("%s_%s.log",groupName,
                timestamp.format(DateTimeFormatter.ISO_DATE_TIME)));

        OutputLogEvent logEvent = OutputLogEvent.builder().message("TEST").timestamp(eventTime).build();

        LinkedList<Stream<OutputLogEventDecorator>> streamList = new LinkedList<>();
        streamNames.forEach(name -> {
            Stream<OutputLogEventDecorator> stream = Stream.of(new OutputLogEventDecorator(logEvent, groupName, name));
            streamList.add(stream);
            logStreams.add(createStream(eventTime, name));});

        Map<String, Map<String, String>> groups = new HashMap<>();
        createExistingGroups(groups);

        EasyMock.expect(logClient.getGroupsWithTags()).andReturn(groups);
        EasyMock.expect(logClient.getStreamsFor(groupName, queryTime)).andReturn(logStreams);
        EasyMock.expect(logClient.fetchLogs(groupName, streamNames, queryTime)).andReturn(streamList);
        EasyMock.expect(savesFile.save(expectedPath, streamList)).andReturn(true);

        replayAll();
        List<Path> filenames = logRepository.fetchLogs(projectAndEnv, Duration.ofDays(days));
        verifyAll();

        Path entry = filenames.get(0);
        assertEquals(expectedPath.toAbsolutePath().toString(), entry.toAbsolutePath().toString());
    }

    @Test
    public void shouldCorrectFormFilenameEvenIfGroupNameIsPath() {
        String exampleGroupName = "/var/log/syslog_20181114T141219.117Z.log";

        Path result = logRepository.formFilenameFor(exampleGroupName, timestamp);
        // make sure file sep chars removed
        assertFalse(result.toAbsolutePath().toString().contains(exampleGroupName));
    }

    private LogStream createStream(long utcEpochMillis, String streamName) {
        return LogStream.builder().logStreamName(streamName).lastEventTimestamp(utcEpochMillis).build();
    }

    private void createExistingGroups(Map<String, Map<String, String>> groups) {
        Map<String, String> groupATags = new HashMap<>();
        Map<String, String> groupBTags = new HashMap<>();
        Map<String, String> groupCTags = new HashMap<>();

        groupATags.put("X", "value");
        groupBTags.put(AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv());
        groupBTags.put(AwsFacade.PROJECT_TAG, projectAndEnv.getProject());
        groupCTags.put(AwsFacade.ENVIRONMENT_TAG, "wrongEnv");
        groupCTags.put(AwsFacade.PROJECT_TAG, "wrongProject");

        groups.put("groupA", groupATags);
        groups.put("groupB", groupBTags);
        groups.put("groupC", groupCTags);
    }
}

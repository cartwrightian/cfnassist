package tw.com.unit;

import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.OutputLogEvent;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

@RunWith(EasyMockRunner.class)
public class TestLogRepository  extends EasyMockSupport {
    private LogClient logClient;
    private ProjectAndEnv projectAndEnv;
    private LogRepository logRepository;
    private DateTime timestamp;
    private SavesFile savesFile;

    @Before
    public void beforeEachTestRuns() {
        timestamp = DateTime.now();
        timestamp = timestamp.minusMinutes(timestamp.getMinuteOfDay()); // use midnight

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

        LogStream streamA = createStream(timestamp.minusDays(20).getMillis(), "streamA");
        LogStream streamB = createStream(timestamp.minusDays(29).getMillis(), "streamB");
        LogStream streamC = createStream(timestamp.minusDays(27).getMillis(), "streamC");
        List<LogStream> streams = Arrays.asList(streamA, streamB, streamC);

        long queryTime = timestamp.minus(Duration.ofDays(days).toMillis()).getMillis();

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
        long queryTime = timestamp.minus(Duration.ofDays(days).toMillis()).getMillis();
        long eventTime = timestamp.minusDays(days-1).getMillis();

        Path expectedPath = Paths.get(format("%s_%s.log",groupName, timestamp.toString(ISODateTimeFormat.basicDateTime())));

        OutputLogEvent logEvent = new OutputLogEvent().withMessage("TEST").withTimestamp(eventTime);

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
        savesFile.save(expectedPath, streamList);

        replayAll();
        List<Path> filenames = logRepository.fetchLogs(projectAndEnv, Duration.ofDays(days));
        verifyAll();

        Path entry = filenames.get(0);
        assertEquals(expectedPath.toAbsolutePath().toString(), entry.toAbsolutePath().toString());
    }

    private LogStream createStream(long offset, String streamName) {
        return new LogStream().withLogStreamName(streamName).withLastEventTimestamp(offset);
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

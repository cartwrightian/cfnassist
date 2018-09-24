package tw.com.unit;

import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.OutputLogEvent;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.OutputLogEventDecorator;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;
import tw.com.repository.LogRepository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@RunWith(EasyMockRunner.class)
public class TestLogRepository  extends EasyMockSupport {
    private LogClient logClient;
    private ProjectAndEnv projectAndEnv;
    private LogRepository logRepository;
    private DateTime timestamp;

    @Before
    public void beforeEachTestRuns() {
        timestamp = DateTime.now();

        projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
        logClient = createStrictMock(LogClient.class);
        logRepository = new LogRepository(logClient, () -> timestamp);
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

        LogStream streamA = createStream(timestamp.minusDays(20).getMillis(), "streamA");
        LogStream streamB = createStream(timestamp.minusDays(29).getMillis(), "streamB");
        LogStream streamC = createStream(timestamp.minusDays(28).getMillis(), "streamC");
        List<LogStream> streams = Arrays.asList(streamA, streamB, streamC);

        EasyMock.expect(logClient.getStreamsFor("groupName")).andReturn(streams);
        logClient.deleteLogStream("groupName", "streamB");
        EasyMock.expectLastCall();

        replayAll();
        logRepository.removeOldStreamsFor("groupName", Duration.ofDays(28));
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

        long epoch = timestamp.minusDays(days).getMillis();

        OutputLogEvent logEvent = new OutputLogEvent().withMessage("TEST").withTimestamp(epoch);

        LinkedList<Stream<OutputLogEventDecorator>> streamList = new LinkedList<>();
        streamNames.forEach(name -> {
            Stream<OutputLogEventDecorator> stream = Stream.of(new OutputLogEventDecorator(logEvent, groupName, name));
            streamList.add(stream);
            logStreams.add(createStream(epoch, name));});

        Map<String, Map<String, String>> groups = new HashMap<>();
        createExistingGroups(groups);

        EasyMock.expect(logClient.getGroupsWithTags()).andReturn(groups);
        EasyMock.expect(logClient.getStreamsFor(groupName)).andReturn(logStreams);
        EasyMock.expect(logClient.fetchLogs(groupName, streamNames, epoch)).andReturn(streamList);

        replayAll();
        Stream<String> result = logRepository.fetchLogs(projectAndEnv, Duration.ofDays(days));
        verifyAll();

        Optional<String> entry = result.findFirst();
        assertTrue(entry.isPresent());
        assertEquals(String.format("groupB %s TEST", timestamp.minusDays(days)), entry.get());
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

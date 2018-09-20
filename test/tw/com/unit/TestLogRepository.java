package tw.com.unit;

import com.amazonaws.services.logs.model.LogStream;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;
import tw.com.providers.ProvidesNow;
import tw.com.repository.LogRepository;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private LogStream createStream(long offset, String streamName) {
        return new LogStream().withLogStreamName(streamName).withLastEventTimestamp(offset);
    }
}

package tw.com.unit;


import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import tw.com.EnvironmentSetupForTests;
import tw.com.StackCache;
import tw.com.entity.StackEntry;
import tw.com.providers.CFNClient;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static tw.com.EnvironmentSetupForTests.createCfnStackTAG;

public class TestStackCache extends EasyMockSupport {

    @Test
    public void shouldGetStackEntry() {

        CFNClient formationClient = createMock(CFNClient.class);
        List<Stack> stacks = new LinkedList<>();
        stacks.add(Stack.builder().tags(
                createCfnStackTAG("CFN_ASSIST_PROJECT",EnvironmentSetupForTests.PROJECT),
                createCfnStackTAG("CFN_ASSIST_ENV", EnvironmentSetupForTests.ENV),
                createCfnStackTAG("CFN_ASSIST_BUILD_NUMBER", "42"),
                createCfnStackTAG("CFN_ASSIST_DELTA", "8"),
                createCfnStackTAG("CFN_ASSIST_UPDATE", "9,10,11")).build());
        EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks);

        StackCache stackCache = new StackCache(formationClient, EnvironmentSetupForTests.PROJECT);

        replayAll();
        List<StackEntry> result = stackCache.getEntries();
        verifyAll();

        Assertions.assertEquals(1, result.size());
        StackEntry stack = result.get(0);
        Assertions.assertEquals(EnvironmentSetupForTests.PROJECT, stack.getProject());
        Assertions.assertEquals(EnvironmentSetupForTests.ENV, stack.getEnvTag().getEnv());
        Assertions.assertEquals(42, stack.getBuildNumber());
        Assertions.assertEquals(8, stack.getIndex());
        Set<Integer> updateIndexs = stack.getUpdateIndex();
        Assertions.assertEquals(3, updateIndexs.size());
        Assertions.assertTrue(updateIndexs.contains(9));
        Assertions.assertTrue(updateIndexs.contains(10));
        Assertions.assertTrue(updateIndexs.contains(11));

    }


}

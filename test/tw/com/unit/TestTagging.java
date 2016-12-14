package tw.com.unit;


import com.amazonaws.services.cloudformation.model.Tag;
import org.junit.Before;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.Tagging;

import java.util.Collection;
import java.util.LinkedHashSet;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class TestTagging {

    private Tagging tagging;

    @Before
    public void beforeEachTestRuns() {
        tagging = new Tagging();
    }

    @Test
    public void shouldAddCommentTag() {
        tagging.setCommentTag("This is a comment tag");
        Collection<Tag> results = getResultsAndCheckSize();

        assertTrue(results.contains(EnvironmentSetupForTests.createCfnStackTAG("CFN_COMMENT", "This is a comment tag")));
    }

    @Test
    public void shouldAddIndexTag() {
        tagging.setIndexTag(42);
        Collection<Tag> results = getResultsAndCheckSize();

        assertTrue(results.contains(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_DELTA", "42")));
    }

    @Test
    public void shouldHaveEqualitySame() {
        tagging.setCommentTag("aComment");
        tagging.setIndexTag(42);
        Tagging anotherTag = new Tagging();
        anotherTag.setCommentTag("aComment");
        anotherTag.setIndexTag(42);

        assertTrue(tagging.equals(anotherTag));
        assertTrue(anotherTag.equals(tagging));
    }

    @Test
    public void shouldHaveEqualityDiffIndex() {
        tagging.setCommentTag("aComment");
        tagging.setIndexTag(43);
        Tagging anotherTag = new Tagging();
        anotherTag.setCommentTag("aComment");
        anotherTag.setIndexTag(42);

        assertFalse(tagging.equals(anotherTag));
        assertFalse(anotherTag.equals(tagging));
    }

    @Test
    public void shouldHaveEqualityDiffComment() {
        tagging.setCommentTag("aCommentA");
        tagging.setIndexTag(42);
        Tagging anotherTag = new Tagging();
        anotherTag.setCommentTag("aCommentB");
        anotherTag.setIndexTag(42);

        assertFalse(tagging.equals(anotherTag));
        assertFalse(anotherTag.equals(tagging));
    }

    @Test
    public void shouldRenderAllToString() {
        tagging.setCommentTag("aComment");
        tagging.setIndexTag(42);

        String result = tagging.toString();
        assertTrue(result.contains("aComment"));
        assertTrue(result.contains("42"));
    }

    private Collection<Tag> getResultsAndCheckSize() {
        Collection<Tag> results = new LinkedHashSet<>();
        tagging.addTagsTo(results);
        assertEquals(1, results.size());
        return results;
    }


}

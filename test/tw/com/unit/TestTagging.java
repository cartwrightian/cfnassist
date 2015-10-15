package tw.com.unit;


import com.amazonaws.services.cloudformation.model.Tag;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.Tagging;

import java.util.Collection;
import java.util.LinkedHashSet;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class TestTagging {

    @Test
    public void shouldAddCommentTag() {
        Tagging tagging = new Tagging();
        tagging.setCommentTag("This is a comment tag");

        Collection<Tag> results = new LinkedHashSet<>();
        tagging.addTagsTo(results);

        assertEquals(1, results.size());
        assertTrue(results.contains(EnvironmentSetupForTests.createCfnStackTAG("CFN_COMMENT","This is a comment tag")));
    }
}

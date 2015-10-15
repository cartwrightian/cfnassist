package tw.com.entity;

import com.amazonaws.services.cloudformation.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class Tagging {
    public static final String COMMENT_TAG = "CFN_COMMENT";

    private static final Logger logger = LoggerFactory.getLogger(Tagging.class);

    private String commentTag = "";

    public void addTagsTo(Collection<Tag> tagCollection) {
        if (!commentTag.isEmpty()) {
            logger.info(String.format("Adding %s: %s", COMMENT_TAG, commentTag));
            tagCollection.add(createTag(COMMENT_TAG, commentTag));
        }
    }

    public void setCommentTag(String commentTag) {
        this.commentTag = commentTag;
    }

    private Tag createTag(String key, String value) {
        Tag tag = new Tag();
        tag.setKey(key);
        tag.setValue(value);
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tagging tagging = (Tagging) o;

        return !(commentTag != null ? !commentTag.equals(tagging.commentTag) : tagging.commentTag != null);

    }

    @Override
    public int hashCode() {
        return commentTag != null ? commentTag.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Tagging{" +
                "commentTag='" + commentTag + '\'' +
                '}';
    }
}

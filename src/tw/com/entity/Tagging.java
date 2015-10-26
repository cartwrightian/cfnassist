package tw.com.entity;

import com.amazonaws.services.cloudformation.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;

import java.util.Collection;
import java.util.Optional;

public class Tagging {
    private static final Logger logger = LoggerFactory.getLogger(Tagging.class);

    public static final String COMMENT_TAG = "CFN_COMMENT";

    private String commentTag = "";
    private Optional<Integer> indexTag = Optional.empty();

    public void addTagsTo(Collection<Tag> tagCollection) {
        if (!commentTag.isEmpty()) {
            addTag(tagCollection, COMMENT_TAG, commentTag);
        }
        if (indexTag.isPresent()) {
            addTag(tagCollection, AwsFacade.INDEX_TAG, indexTag.get().toString());
        }
    }

    private void addTag(Collection<Tag> tagCollection, String tag, String value) {
        logger.info(String.format("Adding %s: %s", tag, value));
        tagCollection.add(createTag(tag, value));
    }

    public void setCommentTag(String commentTag) {
        this.commentTag = commentTag;
    }

    public void setIndexTag(Integer indexTag) {
        this.indexTag = Optional.of(indexTag);
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

        if (commentTag != null ? !commentTag.equals(tagging.commentTag) : tagging.commentTag != null) return false;
        return !(indexTag != null ? !indexTag.equals(tagging.indexTag) : tagging.indexTag != null);

    }

    @Override
    public int hashCode() {
        int result = commentTag != null ? commentTag.hashCode() : 0;
        result = 31 * result + (indexTag != null ? indexTag.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Tagging{" +
                "commentTag='" + commentTag + '\'' +
                ", indexTag=" + indexTag +
                '}';
    }
}

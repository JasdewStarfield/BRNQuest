package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;

/** One immutable artwork replacement is one revision-checked session edit and one undo step. */
public final class CanvasEdits {
    private CanvasEdits() {}
    public static AuthorOperationResult<DraftChange> replace(QuestBookDefinition book, ResourceLocation target, CanvasScene scene) {
        if (book.id().equals(target)) {
            if (!scene.decorations().isEmpty()) throw new IllegalArgumentException("Decorations belong to chapters");
            return AuthorOperationResult.success("CANVAS_UPDATED", "Book backgrounds updated", new DraftChange(
                    new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), book.chapterGroups(), book.chapters(),
                            book.legacyIds(), book.localization(), scene.write(book.extensions()), book.questDefaults(), book.settings()), List.of(target)));
        }
        var chapter = book.chapters().stream().filter(c -> c.id().equals(target)).findFirst().orElse(null);
        if (chapter == null) return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "CHAPTER_NOT_FOUND", "Chapter no longer exists");
        return DraftBookEditor.updateChapter(book, target, new ChapterDefinition(book.id(), target, chapter.groupId(), chapter.title(), chapter.icon(),
                chapter.order(), chapter.quests(), scene.write(chapter.extensions()), chapter.questDefaults(), chapter.consumeItems(),
                chapter.autofocusQuestId(), chapter.defaultHideDependencyLines()));
    }
}

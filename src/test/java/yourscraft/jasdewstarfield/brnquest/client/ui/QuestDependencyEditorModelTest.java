package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDependencyEditorModelTest {
    @Test void marksEveryCandidateThatAlreadyReachesTheSelectedQuest() {
        QuestBookDefinition book = book();

        assertTrue(QuestDependencyEditorModel.wouldCreateCycle(book, id("root"), id("middle")));
        assertTrue(QuestDependencyEditorModel.wouldCreateCycle(book, id("root"), id("leaf")));
        assertFalse(QuestDependencyEditorModel.wouldCreateCycle(book, id("leaf"), id("free")));
    }

    @Test void pickerPreservesBookOrderExcludesExistingEdgesAndSearchesChapterOrId() {
        QuestBookDefinition book = book();

        var all = QuestDependencyEditorModel.candidates(book, id("leaf"), "");
        assertEquals(List.of(id("root"), id("free")), all.stream()
                .map(QuestDependencyEditorModel.Candidate::questId).toList());

        var byChapter = QuestDependencyEditorModel.candidates(book, id("leaf"), "Other Chapter");
        assertEquals(List.of(id("free")), byChapter.stream()
                .map(QuestDependencyEditorModel.Candidate::questId).toList());
        var byId = QuestDependencyEditorModel.candidates(book, id("leaf"), "test:root");
        assertEquals(List.of(id("root")), byId.stream()
                .map(QuestDependencyEditorModel.Candidate::questId).toList());
    }

    @Test void cyclicCandidateSubtitleUsesTranslationSafeQuestIdText() {
        var candidate = new QuestDependencyEditorModel.Candidate(
                id("cross_target"), "Cross target", "Other Chapter", true);

        // Construction itself is the regression boundary: Minecraft throws immediately for unsupported args.
        assertDoesNotThrow(() -> QuestScreen.dependencyCandidateSubtitle(candidate));
    }

    @Test void pickerSearchesLocalizedTitlesAndKeepsStableIdLookup() {
        var source = book();
        var localized = new QuestBookDefinition(source.id(), source.schemaVersion(), source.title(),
                source.chapterGroups(), source.chapters(), source.legacyIds(),
                new yourscraft.jasdewstarfield.brnquest.data.BookLocalization("en_us", Map.of("zh_cn", Map.of(
                        "quest.test:free.title", "自由任务", "chapter.test:other_chapter.title", "其他章节"))), source.extensions());
        var byTitle = QuestDependencyEditorModel.candidates(localized, id("leaf"), "自由", "zh_cn");
        assertEquals(List.of(id("free")), byTitle.stream().map(QuestDependencyEditorModel.Candidate::questId).toList());
        assertEquals("其他章节", byTitle.getFirst().chapterTitle());
        assertEquals("自由任务", QuestDependencyEditorModel.candidates(localized, id("leaf"), "test:free", "zh_cn").getFirst().title());
        assertTrue(QuestDependencyEditorModel.candidates(localized, id("leaf"), "自由", "en_us").isEmpty());
    }

    private static QuestBookDefinition book() {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation firstChapterId = id("first_chapter");
        ResourceLocation otherChapterId = id("other_chapter");
        QuestDefinition root = quest(bookId, id("root"), firstChapterId, "Root", List.of());
        QuestDefinition middle = quest(bookId, id("middle"), firstChapterId, "Middle", List.of(root.id()));
        QuestDefinition leaf = quest(bookId, id("leaf"), otherChapterId, "Leaf", List.of(middle.id()));
        QuestDefinition free = quest(bookId, id("free"), otherChapterId, "Free", List.of());
        ChapterDefinition first = new ChapterDefinition(bookId, firstChapterId, groupId,
                "First Chapter", "", 0, List.of(root, middle));
        ChapterDefinition other = new ChapterDefinition(bookId, otherChapterId, groupId,
                "Other Chapter", "", 1, List.of(leaf, free));
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(first, other), Map.of());
    }

    private static QuestDefinition quest(ResourceLocation bookId, ResourceLocation questId,
                                         ResourceLocation chapterId, String title,
                                         List<ResourceLocation> dependencies) {
        return new QuestDefinition(bookId, questId, chapterId, title, "", "", "",
                0, 0, dependencies, List.of(), List.of(), "");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}

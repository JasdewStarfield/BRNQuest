package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestPresentationTest {
    @Test void navigationUsesGroupAndChapterOrderInsteadOfIds() {
        ResourceLocation bookId = id("book");
        var laterGroup = new ChapterGroupDefinition(bookId, id("a_group"), "Later", 1);
        var firstGroup = new ChapterGroupDefinition(bookId, id("z_group"), "First", 0);
        var secondChapter = chapter(bookId, id("a_chapter"), firstGroup.id(), 2);
        var firstChapter = chapter(bookId, id("z_chapter"), firstGroup.id(), 1);
        var laterChapter = chapter(bookId, id("later"), laterGroup.id(), 0);
        var book = new QuestBookDefinition(bookId, 1, "Book", List.of(laterGroup, firstGroup),
                List.of(secondChapter, laterChapter, firstChapter), Map.of());

        assertEquals(List.of(firstChapter.id(), secondChapter.id(), laterChapter.id()),
                QuestPresentation.orderedChapters(book).stream().map(ChapterDefinition::id).toList());
    }

    @Test void defaultVisualUsesFirstTaskAndNeverAReward() {
        ResourceLocation bookId = id("book");
        ResourceLocation chapterId = id("chapter");
        var item = new TaskDefinition(bookId, id("task"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:rope\"}"), false);
        var quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "", "", "",
                0, 0, List.of(), List.of(item), List.of(), "LEGACY");

        assertEquals(QuestPresentation.VisualKind.ITEM, QuestPresentation.visual(quest).kind());
        assertEquals(item.config().get("item"), QuestPresentation.visual(quest).itemSnbt());
    }

    private ChapterDefinition chapter(ResourceLocation bookId, ResourceLocation id, ResourceLocation group, int order) {
        return new ChapterDefinition(bookId, id, group, id.getPath(), "", order, List.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_test", path);
    }
}

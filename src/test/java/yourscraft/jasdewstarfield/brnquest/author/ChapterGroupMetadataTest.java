package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises metadata through real immutable edits, projections, encoding and recovery copies. */
class ChapterGroupMetadataTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static QuestBookDefinition book(ChapterGroupDefinition group) {
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(group,
                new ChapterGroupDefinition(id("book"), id("other"), "Other", 1)), List.of(), Map.of());
    }
    @Test void oldGroupsRetainTheirEncodingAndCompatibilityConstructor() {
        var legacy = book(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0));
        String json = NativeBookJson.encode(legacy);
        var groupJson = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("chapter_groups").get(0).getAsJsonObject();
        assertEquals(3, groupJson.size(), "Empty optional metadata must not change the old canonical encoding");
        assertEquals(legacy, NativeBookJson.decode(JsonParser.parseString(json).getAsJsonObject()));
        assertEquals("", ApiViews.chapterGroup(QuestBookSnapshot.of(legacy), legacy.chapterGroups().getFirst()).icon());
    }
    @Test void metadataSurvivesReorderRecoveryProjectionAndJson() {
        var group = new ChapterGroupDefinition(id("book"), id("group"), "Group", 0,
                "texture:test:group.png", "A description", Map.of("addon.config", "{nested:[1,2]}"));
        var source = book(group);
        var moved = DraftBookEditor.moveGroup(source, group.id(), 1).value().book();
        var recovered = DraftService.recoveryCopy(moved, id("recovered"));
        var decoded = NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(recovered)).getAsJsonObject());
        var result = decoded.chapterGroups().stream().filter(g -> g.id().equals(group.id())).findFirst().orElseThrow();
        assertEquals(1, result.order());
        assertEquals(id("recovered"), result.bookId());
        assertEquals(group.icon(), result.icon());
        assertEquals(group.description(), result.description());
        assertEquals(group.extensions(), result.extensions());
        var view = ApiViews.chapterGroup(QuestBookSnapshot.of(decoded), result, "zh_cn");
        assertEquals(result.icon(), view.icon());
        assertEquals(result.description(), view.description());
        assertEquals(result.extensions(), view.extensions());
        assertThrows(UnsupportedOperationException.class, () -> view.extensions().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> result.extensions().put("x", "y"));
    }
    @Test void updatesProduceExplicitReviewFieldsWithoutMutatingOriginal() {
        var source = book(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0));
        var replacement = new ChapterGroupDefinition(id("book"), id("group"), "Group", 0,
                "texture:test:group.png", "Text", Map.of("addon", "keep"));
        var changed = DraftBookEditor.updateGroup(source, replacement.id(), replacement).value().book();
        var paths = QuestBookDiffer.diff(source, changed).entries().stream().map(SemanticDiffEntry::path).toList();
        assertTrue(paths.containsAll(List.of("icon", "description", "extensions.addon")));
        assertEquals("", source.chapterGroups().getFirst().icon());
        assertNotEquals(QuestBookSnapshot.of(source).revision(), QuestBookSnapshot.of(changed).revision());
    }
    @Test void localizedGroupPropertiesDoNotOverwriteOtherLanguages() {
        var group = new ChapterGroupDefinition(id("book"), id("group"), "Native", 0);
        var source = new QuestBookDefinition(id("book"), 1, "Book", List.of(group), List.of(), Map.of("ABC", group.id()),
                new BookLocalization("en_us", Map.of("en_us", Map.of("chapter_group.ABC.title", "English"),
                        "zh_cn", Map.of("chapter_group.ABC.title", "中文"))), Map.of());
        var replacement = new ChapterGroupDefinition(group.bookId(), group.id(), "新标题", 0, "texture:test:icon.png", "Info", Map.of());
        var edited = DraftBookEditor.updateGroupProperties(source, group.id(), replacement, "zh_cn").value().book();
        assertEquals("新标题", BookText.structureTitle(edited, "chapter_group", group.id(), "zh_cn", ""));
        assertEquals("English", BookText.structureTitle(edited, "chapter_group", group.id(), "en_us", ""));
        assertEquals("Native", edited.chapterGroups().getFirst().title());
        var english = new ChapterGroupDefinition(group.bookId(), group.id(), "New English", 0, "", "Info", Map.of());
        edited = DraftBookEditor.updateGroupProperties(edited, group.id(), english, "en_us").value().book();
        assertEquals("New English", edited.chapterGroups().getFirst().title());
        assertEquals("新标题", BookText.structureTitle(edited, "chapter_group", group.id(), "zh_cn", ""));
        assertFalse(edited.localization().translations().getOrDefault("en_us", Map.of()).containsKey("chapter_group.ABC.title"));
    }

}

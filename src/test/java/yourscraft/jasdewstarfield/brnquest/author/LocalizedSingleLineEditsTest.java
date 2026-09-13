package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalizedSingleLineEditsTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static QuestBookDefinition book() {
        var quest = new QuestDefinition(id("book"), id("q"), id("c"), "Title", "Subtitle", "Description", "",
                0, 0, List.of(), List.of(), List.of(), "ABC");
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("g"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("c"), id("g"), "Chapter", "", 0, List.of(quest))), Map.of("GROUP", id("g")),
                new BookLocalization("en_us", Map.of("en_us", Map.of("quest.ABC.title", "Imported", "quest.ABC.quest_subtitle", "Imported subtitle"),
                        "zh_cn", Map.of("quest.ABC.quest_desc", "Chinese description"))), Map.of());
    }
    @Test void titleBatchPreservesSubtitleDescriptionAndOtherLocales() {
        var source = book();
        var changed = LocalizedSingleLineEdits.apply(source, "quest", id("q"), "title", Map.of("en_us", "New title", "zh_cn", "Chinese title"));
        assertEquals("New title", changed.quests().getFirst().title());
        assertEquals("Subtitle", changed.quests().getFirst().subtitle());
        assertEquals("Description", changed.quests().getFirst().description());
        assertEquals(Map.of("quest.ABC.quest_subtitle", "Imported subtitle"), changed.localization().translations().get("en_us"));
        assertEquals(Map.of("quest.ABC.quest_desc", "Chinese description", "quest.ABC.title", "Chinese title"), changed.localization().translations().get("zh_cn"));
        assertEquals("Title", source.quests().getFirst().title());
    }
    @Test void subtitleEditDoesNotMaterializeTitleFallback() {
        var changed = LocalizedSingleLineEdits.apply(book(), "quest", id("q"), "quest_subtitle", Map.of("fr_fr", "French subtitle"));
        assertEquals(Map.of("quest.ABC.quest_subtitle", "French subtitle"), changed.localization().translations().get("fr_fr"));
        assertEquals("Title", changed.quests().getFirst().title());
    }
    @Test void structuralTitlesUseTheSameLegacyKeysAsDisplay() {
        var changed = LocalizedSingleLineEdits.apply(book(), "chapter_group", id("g"), "title", Map.of("zh_cn", "Chinese group"));
        changed = LocalizedSingleLineEdits.apply(changed, "chapter", id("c"), "title", Map.of("en_us", "New chapter", "fr_fr", "French chapter"));
        assertEquals("Chinese group", BookText.structureTitle(changed, "chapter_group", id("g"), "zh_cn", ""));
        assertEquals("New chapter", changed.chapters().getFirst().title());
        assertEquals("French chapter", BookText.structureTitle(changed, "chapter", id("c"), "fr_fr", ""));
        assertEquals(book().quests(), changed.quests());
    }
    @Test void invalidTargetsAndPayloadsAreRejectedBeforeChangingSource() {
        var source = book();
        assertThrows(IllegalArgumentException.class, () -> LocalizedSingleLineEdits.apply(source, "chapter", id("c"), "quest_subtitle", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> LocalizedSingleLineEdits.apply(source, "quest", id("q"), "title", Map.of("../../x", "x")));
        assertThrows(IllegalArgumentException.class, () -> LocalizedSingleLineEdits.apply(source, "quest", id("q"), "title", Map.of("zh_cn", "line1\nline2")));
        assertEquals(source, LocalizedSingleLineEdits.apply(source, "quest", id("q"), "title", Map.of()));
    }
}

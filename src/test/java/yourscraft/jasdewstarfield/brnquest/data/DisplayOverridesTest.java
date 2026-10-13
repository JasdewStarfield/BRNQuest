package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.ChapterCopyEdits;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Shared overrides must survive author content operations without altering unrelated addon values. */
class DisplayOverridesTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static QuestBookDefinition book(Map<String, String> questExtensions, Map<String, String> chapterExtensions) {
        var quest = new QuestDefinition(id("book"), id("quest"), id("chapter"), "Quest", "", "", "",
                0, 0, List.of(), List.of(), List.of(), "", QuestAppearance.DEFAULT, questExtensions);
        var chapter = new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0,
                List.of(quest), chapterExtensions);
        return new QuestBookDefinition(id("book"), 1, "Book",
                List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)), List.of(chapter), Map.of());
    }

    @Test void authoredOffAndHideOverrideEnabledClientPreferencesWhileOldBooksInherit() {
        assertEquals(DisplayOverrides.NamePosition.ABOVE,
                DisplayOverrides.name(Map.of()).resolve(DisplayOverrides.NamePosition.ABOVE));
        assertTrue(DisplayOverrides.progress(Map.of()).resolve(true));
        assertFalse(DisplayOverrides.progress(Map.of()).resolve(false));
        assertEquals(DisplayOverrides.NamePosition.OFF, DisplayOverrides.name(Map.of(DisplayOverrides.QUEST_NAME_KEY, "off"))
                .resolve(DisplayOverrides.NamePosition.BELOW));
        assertFalse(DisplayOverrides.progress(Map.of(DisplayOverrides.CHAPTER_PROGRESS_KEY, "hide")).resolve(true));
        assertTrue(DisplayOverrides.progress(Map.of(DisplayOverrides.CHAPTER_PROGRESS_KEY, "show")).resolve(false));
    }

    @Test void omittedFieldsPreserveOverridesAndExplicitInheritRemovesOnlyOwnedKeys() {
        var base = Map.of("addon:opaque", "keep");
        var quest = DisplayOverrides.updateName(base, "above");
        assertEquals(quest, DisplayOverrides.updateName(quest, null));
        assertEquals(base, DisplayOverrides.updateName(quest, "inherit"));
        var chapter = DisplayOverrides.updateProgress(base, Map.of("chapter_progress", "hide"));
        assertEquals(chapter, DisplayOverrides.updateProgress(chapter, Map.of()));
        assertEquals(base, DisplayOverrides.updateProgress(chapter, Map.of("chapter_progress", "inherit")));
        assertEquals(Map.of("addon:opaque", "keep"), base);
    }

    @Test void nativeRoundTripAndChapterCopyPreserveSharedProperties() {
        var source = book(QuestNotificationPolicy.apply(DisplayOverrides.updateName(Map.of("addon:quest", "keep"), "below"),
                        Map.of(QuestNotificationPolicy.TASK, "false", QuestNotificationPolicy.REWARD, "true")),
                DisplayOverrides.updateProgress(Map.of("addon:chapter", "keep"), Map.of("chapter_progress", "show")));
        var loaded = NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(source)).getAsJsonObject());
        assertEquals(source, loaded);
        var copied = ChapterCopyEdits.copy(loaded, id("chapter"));
        assertTrue(copied.success(), copied.message());
        var copy = copied.value().book().chapters().stream().filter(c -> !c.id().equals(id("chapter"))).findFirst().orElseThrow();
        assertEquals(source.chapters().getFirst().extensions(), copy.extensions());
        assertEquals(source.quests().getFirst().extensions(), copy.quests().getFirst().extensions());
    }

    @Test void invalidSharedValuesAreRejectedDuringNativeLoadAndAuthorMutation() {
        assertThrows(IllegalArgumentException.class, () -> book(Map.of(DisplayOverrides.QUEST_NAME_KEY, "sideways"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> book(Map.of(), Map.of(DisplayOverrides.CHAPTER_PROGRESS_KEY, "yes")));
        assertThrows(IllegalArgumentException.class, () -> DisplayOverrides.updateName(Map.of(), "sideways"));
    }
}

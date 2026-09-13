package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Numbering is shared across locales and preserves the distinction between absent and explicit text. */
class QuestCopyTitlesTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static QuestDefinition quest(String id, String title) {
        return new QuestDefinition(id("book"), id(id), id("chapter"), title, "Subtitle", "Body", "", 0, 0, List.of(), List.of(), List.of(), "");
    }
    @Test void repeatedCopiesNumberEveryExistingLocaleAndKeepMissingLocalesAbsent() {
        var source = quest("source", "Quest");
        var book = new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, List.of(source))), Map.of(),
                new BookLocalization("en_us", Map.of("zh_cn", Map.of("quest.test:source.title", "任务", "quest.test:source.subtitle", "副标题"),
                        "en_us", Map.of("quest.test:source.title", "Quest"), "ja_jp", Map.of("unrelated", "keep"))), Map.of());
        for (int i = 1; i <= 3; i++) {
            assertEquals(i, QuestCopyTitles.nextNumber(book, source));
            var copy = quest("copy" + i, QuestCopyTitles.suggestedTitle(book, source));
            book = DraftBookEditor.copyQuest(book, source.id(), copy).value().book();
            assertEquals(i == 1 ? "任务（副本）" : "任务（副本 " + i + "）", BookText.quest(book, copy, "zh_cn", "title", ""));
            assertEquals(copy.title(), BookText.quest(book, copy, "en_us", "title", ""));
            assertEquals("副标题", BookText.quest(book, copy, "zh_cn", "subtitle", ""));
            assertFalse(book.localization().translations().get("ja_jp").containsKey(BookText.questPrefix(copy) + "title"));
            assertEquals("Body", copy.description());
        }
        assertEquals("任务", BookText.quest(book, source, "zh_cn", "title", ""));
        assertEquals("Quest (Copy 4)", QuestCopyTitles.suggestedTitle(book, book.quests().get(1)));
        assertEquals(book, NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject()));
    }
}

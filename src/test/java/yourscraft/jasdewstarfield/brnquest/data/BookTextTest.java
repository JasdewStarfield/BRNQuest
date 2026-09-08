package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Covers the shared client/API contract with literal text and imported semantic keys. */
class BookTextTest {
    @Test void requestedConfiguredAndNativeFallbackRemainLiteral() {
        var localization = new BookLocalization("en-US", Map.of(
                "zh_cn", Map.of("title", "中文", "blank", " "),
                "en_us", Map.of("title", "English", "blank", "Configured")));
        assertEquals("中文", localization.resolve("ZH-CN", "title", "Native"));
        assertEquals("English", localization.resolve("ja_jp", "title", "Native"));
        assertEquals("Configured", localization.resolve("zh_cn", "blank", "Native"));
        assertEquals("screen.example.literal", localization.resolve("ja_jp", "absent", "screen.example.literal"));
    }

    @Test void equivalentLocalesMergeWithoutDiscardingTextAndRejectConflicts() {
        var merged = new BookLocalization("en_us", Map.of("zh-CN", Map.of("a", "甲"),
                "ZH_cn", Map.of("a", "甲", "b", "乙")));
        assertEquals(Map.of("a", "甲", "b", "乙"), merged.translations().get("zh_cn"));
        assertThrows(UnsupportedOperationException.class, () -> merged.translations().get("zh_cn").put("c", "丙"));
        assertThrows(IllegalArgumentException.class, () -> new BookLocalization("en_us",
                Map.of("zh-CN", Map.of("a", "甲"), "zh_cn", Map.of("a", "乙"))));
    }

    @Test void localizedViewsPreserveSourceIdentityAndRoundTripAllLocales() {
        var bookId = id("book");
        var group = new ChapterGroupDefinition(bookId, id("group"), "Group", 0);
        var quest = new QuestDefinition(bookId, id("quest"), id("chapter"), "Quest", "Subtitle", "Description",
                "", 1, 2, List.of(), List.of(), List.of(), "OLD_Q");
        var chapter = new ChapterDefinition(bookId, id("chapter"), group.id(), "Chapter", "", 0, List.of(quest));
        var localization = new BookLocalization("en_us", Map.of("zh_cn", Map.of("title", "任务书",
                "chapter_group.OLD_G.title", "章节组", "chapter.OLD_C.title", "章节",
                "quest.OLD_Q.title", "任务", "quest.OLD_Q.quest_subtitle", "副标题",
                "quest.OLD_Q.quest_desc", "第一行\n第二行", "extension.unknown", "保留"),
                "ja_jp", Map.of("title", "本")));
        var book = new QuestBookDefinition(bookId, 1, "Book", List.of(group), List.of(chapter),
                Map.of("OLD_G", group.id(), "OLD_C", chapter.id(), "@ignored", chapter.id(),
                        "ZZ_SECOND_ALIAS", chapter.id(), "OLD_Q", quest.id()), localization, Map.of());
        String encoded = NativeBookJson.encode(book);
        var decoded = NativeBookJson.decode(JsonParser.parseString(encoded).getAsJsonObject());
        var snapshot = QuestBookSnapshot.of(decoded);
        assertEquals(encoded, NativeBookJson.encode(decoded));
        assertEquals(localization, decoded.localization());
        assertEquals("任务书", ApiViews.book(snapshot, "zh-CN").title());
        assertEquals("章节组", ApiViews.chapterGroup(snapshot, group, "zh_cn").title());
        assertEquals("章节", ApiViews.chapter(snapshot, chapter, "zh_cn").title());
        assertEquals("任务", BookText.quest(decoded, quest, "zh_cn", "title", quest.title()));
        assertEquals("第一行\n第二行", ApiViews.quest(snapshot, quest, "zh_cn").description());
        assertEquals("Book", ApiViews.book(snapshot).title());
        assertEquals("Quest", ApiViews.quest(quest).title());
        assertEquals("Chapter", ApiViews.chapter(snapshot, chapter, "de_de").title());
        assertEquals(ApiViews.book(snapshot).questIds(), ApiViews.book(snapshot, "zh_cn").questIds());
        assertEquals(snapshot.revision(), ApiViews.book(snapshot, "ja_jp").revision());
        // Older native books omit localization entirely and keep their original text.
        var oldJson = JsonParser.parseString(encoded).getAsJsonObject();
        oldJson.remove("localization");
        assertEquals("Book", BookText.title(NativeBookJson.decode(oldJson), "zh_cn"));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
}

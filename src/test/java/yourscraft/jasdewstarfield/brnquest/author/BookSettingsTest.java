package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Policies survive immutable rebuilds and reject malformed author data before persistence. */
class BookSettingsTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static QuestBookDefinition book() {
        var chapter = new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0,
                List.of(), Map.of(), QuestCreationDefaults.EMPTY, false);
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(chapter), Map.of(), BookLocalization.EMPTY, Map.of(), QuestCreationDefaults.EMPTY,
                new BookSettings(true, true, "auto_hidden", true, true));
    }
    @Test void codecViewsAndMetadataEditsPreservePolicies() {
        var book = book();
        assertEquals(book, NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject()));
        var edited = DraftBookEditor.updateBookProperties(book, QuestCreationDefaults.EMPTY, "en_us", Map.of()).value().book();
        assertEquals(book.settings(), edited.settings());
        assertEquals(Boolean.FALSE, edited.chapters().getFirst().consumeItems());
        assertEquals("false", book.settings().taskDefaults(book.chapters().getFirst()).get("consume_items"));
        assertEquals("true", ApiViews.book(QuestBookSnapshot.of(book)).settings().get("pause_game"));
        assertEquals(Boolean.FALSE, ApiViews.chapter(book.chapters().getFirst()).consumeItems());
    }
    @Test void teamPoliciesRoundTripAndKeepLegacyDefaults() {
        assertTrue(BookSettings.fromJson(JsonParser.parseString("{}")).shareTeamProgress());
        var settings = BookSettings.fromJson(JsonParser.parseString("{\"share_team_progress\":false}"));
        assertFalse(BookSettings.fromJson(settings.toJson()).shareTeamProgress());
        var defaults = new QuestCreationDefaults(Map.of("require_all_team_members", "true"));
        assertTrue(defaults.behavior().requireAllTeamMembers());
        assertFalse(QuestBehavior.DEFAULT.requireAllTeamMembers());
        var quest = new QuestDefinition(id("book"), id("quest"), id("chapter"), "", "", "", "", 0, 0,
                List.of(), List.of(), List.of(), "", QuestAppearance.DEFAULT, defaults.behavior(), Map.of());
        var book = new QuestBookDefinition(id("book"), 1, "", List.of(),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "", "", 0, List.of(quest))),
                Map.of(), BookLocalization.EMPTY, Map.of(), defaults, settings);
        var decoded = NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject());
        assertEquals(book, decoded);
        assertTrue(ApiViews.quest(decoded.quests().getFirst()).behavior().requireAllTeamMembers());
        var codecJson = QuestBehavior.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, defaults.behavior()).getOrThrow();
        assertTrue(QuestBehavior.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, codecJson).getOrThrow().requireAllTeamMembers());
        assertThrows(IllegalArgumentException.class, () -> BookSettings.fromJson(
                JsonParser.parseString("{\"share_team_progress\":\"false\"}")));
    }
    @Test void oldBooksOmitDefaultSettingsAndInvalidPoliciesFailStrictly() {
        var old = new QuestBookDefinition(id("book"), 1, "Old", List.of(), List.of(), Map.of());
        assertFalse(NativeBookJson.encode(old).contains("\"settings\""));
        for (String invalid : List.of("null", "[]", "{\"pause_game\":\"true\"}", "{\"reward_claim_policy\":\"typo\"}", "{\"extra\":false}"))
            assertThrows(IllegalArgumentException.class, () -> BookSettings.fromJson(JsonParser.parseString(invalid)));
    }
}

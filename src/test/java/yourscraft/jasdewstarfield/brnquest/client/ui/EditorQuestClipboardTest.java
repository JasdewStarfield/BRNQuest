package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** World isolation and rejected-copy behavior must not leak or destroy a usable clipboard. */
class EditorQuestClipboardTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static QuestClipboardSnapshot snapshot(String body) {
        var q = new QuestDefinition(id("book"), id("q"), id("c"), "Q", "", body, "", 0, 0, List.of(), List.of(), List.of(), "");
        return new QuestClipboardSnapshot(new QuestBookDefinition(id("book"), 1, "", List.of(),
                List.of(new ChapterDefinition(id("book"), id("c"), id("g"), "", "", 0, List.of(q))), Map.of(), BookLocalization.EMPTY, Map.of()));
    }
    @Test void scopesClipboardAndKeepsPreviousSnapshotWhenCopyExceedsLimit() {
        var frozen = snapshot("body"); EditorQuestClipboard.copy("world-a", frozen);
        assertEquals(frozen, EditorQuestClipboard.get("world-a", id("book")).orElseThrow());
        assertTrue(EditorQuestClipboard.get("world-b", id("book")).isEmpty());
        assertTrue(EditorQuestClipboard.get("world-a", id("other")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> EditorQuestClipboard.copy("world-a", snapshot("x".repeat(65537))));
        assertEquals(frozen, EditorQuestClipboard.get("world-a", id("book")).orElseThrow());
    }
}

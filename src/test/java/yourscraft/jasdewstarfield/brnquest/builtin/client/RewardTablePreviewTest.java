package yourscraft.jasdewstarfield.brnquest.builtin.client;
import yourscraft.jasdewstarfield.brnquest.builtin.client.RewardTablePreview;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RewardTablePreviewTest {
    private static final String TREE = """
            {"version":1,"mode":"choice","entries":[
              {"entry_id":"bundle","type":"brnquest:reward_table","table":{"mode":"random",
                "rolls":2,"replacement":false,"empty_weight":1,"entries":[
                  {"entry_id":"xp","type":"brnquest:xp","config":{"xp":"12"},"always":true},
                  {"entry_id":"item","type":"brnquest:item","config":{"count":"3"},"weight":2}]}},
              {"entry_id":"xp","type":"brnquest:xp_levels","config":{"xp_levels":"2"}}]}
            """;

    @Test void nestedLocalIdsHaveDistinctStablePathsAndKeepRootIdentity() {
        var root = root(TREE);
        var tree = RewardTablePreview.parse(root);
        var nested = tree.entries().getFirst().child().orElseThrow();
        assertEquals("root/bundle/xp", nested.entries().getFirst().path());
        assertEquals("root/xp", tree.entries().getLast().path());
        assertEquals(root.id(), nested.entries().getFirst().reward().id());
        assertEquals(root.bookId(), nested.entries().getFirst().reward().bookId());
        assertEquals(2, nested.rolls());
        assertFalse(nested.replacement());
        assertTrue(nested.entries().getFirst().guaranteed());
        assertThrows(UnsupportedOperationException.class, () -> tree.entries().clear());
    }

    @Test void reorderAndEditDoNotReuseAnOldSnapshotOrAnIndexIdentity() {
        var old = RewardTablePreview.parse(root(TREE));
        var json = JsonParser.parseString(TREE).getAsJsonObject();
        var entries = json.getAsJsonArray("entries");
        var bundle = entries.remove(0); entries.add(bundle);
        entries.get(0).getAsJsonObject().getAsJsonObject("config").addProperty("xp_levels", "7");
        var updated = RewardTablePreview.parse(root(json.toString()));
        assertEquals(old.entries().getLast().path(), updated.entries().getFirst().path());
        assertEquals("2", old.entries().getLast().reward().config().get("xp_levels"));
        assertEquals("7", updated.entries().getFirst().reward().config().get("xp_levels"));
    }

    @Test void legacyBrowserKeepsHierarchyRulesAndResolvesLabelsEachTime() {
        var tree = RewardTablePreview.parse(root(TREE));
        var first = tree.lines(view -> Component.literal("First language"));
        var second = tree.lines(view -> Component.literal("Second language"));
        assertTrue(first.stream().anyMatch(line -> line.getString().contains("First language")));
        assertTrue(second.stream().noneMatch(line -> line.getString().contains("First language")));
        assertEquals(7, second.size());
        assertTrue(second.get(4).getString().startsWith("    Second language"));
        assertTrue(second.get(2).getString().contains("screen.brnquest.reward_table.mode.random"));
    }

    private static RewardView root(String table) {
        return new RewardView(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:reward"),
                ResourceLocation.parse("brnquest:reward_table"), Map.of("table", table), "manual", false);
    }
}

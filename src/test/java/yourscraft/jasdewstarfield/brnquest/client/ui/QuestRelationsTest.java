package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Relations span chapters but never bypass visibility or include indirect descendants. */
class QuestRelationsTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static QuestDefinition quest(String name, String chapter, String... dependencies) {
        return new QuestDefinition(id("book"), id(name), id(chapter), name, "", "", "", 0, 0,
                java.util.Arrays.stream(dependencies).map(QuestRelationsTest::id).toList(), List.of(), List.of(), "");
    }
    @Test void directNeighborsAreOrderedAcrossChaptersAndFilteredWithoutLeakingHiddenQuests() {
        var parent = quest("parent", "first"); var selected = quest("selected", "second", "parent", "missing");
        var child = quest("child", "third", "selected"); var grandchild = quest("grandchild", "third", "child");
        var hidden = quest("hidden", "first", "selected");
        var book = List.of(parent, selected, child, grandchild, hidden);
        var visible = QuestRelations.resolve(book, selected.id(), q -> !q.id().equals(hidden.id()));
        assertEquals(List.of(parent), visible.upstream()); assertEquals(List.of(child), visible.downstream());
        assertTrue(visible.contains(parent.id())); assertTrue(visible.contains(child.id()));
        assertFalse(visible.contains(grandchild.id())); assertFalse(visible.contains(hidden.id())); assertFalse(visible.contains(null));
        assertEquals(List.of(child, hidden), QuestRelations.resolve(book, selected.id(), q -> true).downstream());
        assertTrue(QuestRelations.resolve(book, id("deleted"), q -> true).upstream().isEmpty());
    }
    @Test void edgeColorsFollowDirectionAndOnlyTouchTheFocusedQuest() {
        assertEquals(QuestRelations.UPSTREAM_COLOR, QuestRelations.edgeColor(id("parent"), id("selected"), id("selected"), 0));
        assertEquals(QuestRelations.DOWNSTREAM_COLOR, QuestRelations.edgeColor(id("selected"), id("child"), id("selected"), 0));
        assertEquals(0, QuestRelations.edgeColor(id("child"), id("grandchild"), id("selected"), 0));
        assertEquals(0, QuestRelations.edgeColor(id("selected"), id("child"), null, 0));
        assertNotEquals(QuestRelations.UPSTREAM_COLOR, QuestRelations.DOWNSTREAM_COLOR);
    }
}

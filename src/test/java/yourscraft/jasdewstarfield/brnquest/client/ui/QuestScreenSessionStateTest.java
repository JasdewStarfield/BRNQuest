package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestScreenSessionStateTest {
    @BeforeEach void reset() {
        QuestScreenSessionState.clearForTest();
    }

    @Test void foldedGroupsAreCopiedAndIsolatedByServerAndBook() {
        var book = ResourceLocation.parse("test:book");
        var group = ResourceLocation.parse("test:group");
        var groups = new java.util.HashSet<ResourceLocation>(); groups.add(group);
        QuestScreenSessionState.save("a", book, null, 0, 0, 1, false, groups);
        groups.clear();
        var restored = QuestScreenSessionState.load("a", book);
        assertEquals(java.util.Set.of(group), restored.foldedGroups());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> restored.foldedGroups().clear());
        assertEquals(java.util.Set.of(), QuestScreenSessionState.load("b", book).foldedGroups());
        assertEquals(java.util.Set.of(), QuestScreenSessionState.load("a", ResourceLocation.parse("test:other")).foldedGroups());
    }

    @Test void editingPreferenceIsIsolatedAndExplicitExitClearsIt() {
        assertEquals(false, QuestScreenSessionState.reopenEditing("server-a"));
        QuestScreenSessionState.rememberEditing("server-a", true);
        assertEquals(true, QuestScreenSessionState.reopenEditing("server-a"));
        assertEquals(false, QuestScreenSessionState.reopenEditing("server-b"));
        QuestScreenSessionState.rememberEditing("server-a", false);
        assertEquals(false, QuestScreenSessionState.reopenEditing("server-a"));
    }

    @Test void firstScreenStartsWithCollapsedNavigation() {
        assertEquals(true, QuestScreenSessionState.Snapshot.defaults().navigationCollapsed());
    }

    @Test void sessionSnapshotRetainsChapterCameraZoomAndNavigation() {
        ResourceLocation chapter = ResourceLocation.fromNamespaceAndPath("brnquest_test", "chapter");

        ResourceLocation book = ResourceLocation.fromNamespaceAndPath("brnquest_test", "book");
        QuestScreenSessionState.save("server-a", book, chapter, 34.5, -68.25, 1.4, false);
        var restored = QuestScreenSessionState.load("server-a", book);

        assertEquals(chapter, restored.chapterId());
        assertEquals(34.5, restored.centerX());
        assertEquals(-68.25, restored.centerY());
        assertEquals(1.4, restored.zoom());
        assertEquals(false, restored.navigationCollapsed());
    }

    @Test void isolatesViewportByServerAndBook() {
        ResourceLocation firstBook = ResourceLocation.parse("test:first");
        ResourceLocation secondBook = ResourceLocation.parse("test:second");
        ResourceLocation chapter = ResourceLocation.parse("test:chapter");
        QuestScreenSessionState.save("server-a", firstBook, chapter, 10.0, 20.0, 1.2, false);

        assertEquals(10.0, QuestScreenSessionState.load("server-a", firstBook).centerX());
        assertEquals(0.0, QuestScreenSessionState.load("server-a", secondBook).centerX());
        assertEquals(0.0, QuestScreenSessionState.load("server-b", firstBook).centerX());
    }
}

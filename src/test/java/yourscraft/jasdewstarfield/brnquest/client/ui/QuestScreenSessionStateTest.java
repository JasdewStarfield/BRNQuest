package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestScreenSessionStateTest {
    @BeforeEach void reset() {
        QuestScreenSessionState.clearForTest();
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

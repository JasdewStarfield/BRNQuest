package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestScreenSessionStateTest {
    @Test void firstScreenStartsWithCollapsedNavigation() {
        assertEquals(true, QuestScreenSessionState.Snapshot.defaults().navigationCollapsed());
    }

    @Test void sessionSnapshotRetainsChapterCameraZoomAndNavigation() {
        ResourceLocation chapter = ResourceLocation.fromNamespaceAndPath("brnquest_test", "chapter");

        QuestScreenSessionState.save(chapter, 34.5, -68.25, 1.4, false);
        var restored = QuestScreenSessionState.load();

        assertEquals(chapter, restored.chapterId());
        assertEquals(34.5, restored.centerX());
        assertEquals(-68.25, restored.centerY());
        assertEquals(1.4, restored.zoom());
        assertEquals(false, restored.navigationCollapsed());
    }
}

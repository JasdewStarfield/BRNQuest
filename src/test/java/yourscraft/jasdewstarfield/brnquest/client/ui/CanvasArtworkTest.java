package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies drag math and clipboard scope without treating headless rendering as visual acceptance. */
class CanvasArtworkTest {
    private CanvasScene.Decoration decoration(boolean aspect) {
        return new CanvasScene.Decoration(ResourceLocation.parse("test:art"), "test:textures/art.png", 1.2, -2.4, 4, 2, aspect, 7, false);
    }
    @Test void dragUsesQuestGridAndDoesNotAlterDimensionsOrFlags() {
        var source = decoration(true);
        var snapped = CanvasArtwork.transformed(source, 0.6, 0.6, false, true);
        assertEquals(2, snapped.x()); assertEquals(-2, snapped.y());
        assertEquals(source, snapped.placed(source.id(), source.x(), source.y(), source.width(), source.height()));
        var free = CanvasArtwork.transformed(source, 0.6, 0.6, false, false);
        assertEquals(1.8, free.x(), 1e-9); assertEquals(-1.8, free.y(), 1e-9);
    }
    @Test void resizingKeepsRatioAndClampsExtremeTravel() {
        var source = decoration(true);
        for (double movement : new double[]{-10000, -1, 1, 10000}) {
            var resized = CanvasArtwork.transformed(source, movement, movement, true, true);
            assertEquals(2, resized.width()/resized.height(), 1e-9);
            assertTrue(resized.width() <= 1024 && resized.height() >= 0.05);
            assertEquals(source.x(), resized.x()); assertEquals(source.y(), resized.y());
        }
        var free = CanvasArtwork.transformed(decoration(false), 2, -1, true, false);
        assertEquals(6, free.width()); assertEquals(1, free.height());
    }
    @Test void decorationClipboardIsAnImmutableSameWorldSameBookSnapshot() {
        var book = ResourceLocation.parse("test:book"); var source = decoration(true);
        EditorDecorationClipboard.copy("world-a", book, source);
        assertEquals(source, EditorDecorationClipboard.get("world-a", book).orElseThrow());
        assertTrue(EditorDecorationClipboard.get("world-b", book).isEmpty());
        assertTrue(EditorDecorationClipboard.get("world-a", ResourceLocation.parse("test:other")).isEmpty());
        var first = source.placed(CanvasScene.newId(), 0, 0, 4, 2);
        var second = source.placed(CanvasScene.newId(), 0, 0, 4, 2);
        assertNotEquals(first.id(), second.id());
        assertEquals(source, EditorDecorationClipboard.get("world-a", book).orElseThrow());
    }

    @Test void rightClickSelectsTopDecorationWithoutStartingAnyGesture() {
        var book = ResourceLocation.parse("test:book"); var chapter = ResourceLocation.parse("test:chapter");
        var editing = new QuestScreenFrameIdentity(book, "r1", true, 300, 240);
        var camera = new QuestCanvasRenderer.Camera(0, 0, 1);
        var viewport = new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(0, 0, 300, 240);
        var frame = QuestCanvasRenderer.composeFrame(new QuestCanvasRenderer.Model(editing, chapter, viewport, camera, java.util.List.of(), false, 0, 10, 10));
        var lower = decoration(true).placed(ResourceLocation.parse("test:lower"), 0, 0, 4, 2);
        var upper = new CanvasScene.Decoration(ResourceLocation.parse("test:upper"), lower.texture(), 0, 0, 4, 2, true, 8, true);
        var controller = new CanvasArtwork();
        controller.prepare(editing, chapter, new CanvasScene(java.util.List.of(lower, upper), null, null), camera);
        assertTrue(controller.click(frame, editing, 10, 10, 1));
        assertEquals(upper, controller.selected(editing, chapter));
        assertFalse(controller.dragging());
        assertFalse(controller.drag(editing, 50, 50, 0));
        assertNull(controller.release(editing, chapter));
        controller.prepare(editing, chapter, new CanvasScene(java.util.List.of(lower), null, null), camera);
        assertTrue(controller.click(frame, editing, 10, 10, 1));
        assertEquals(lower, controller.selected(editing, chapter));
        assertFalse(controller.dragging());
    }

    @Test void browsingLockedArtworkAndStaleFramesCannotMoveDecorations() {
        var book = ResourceLocation.parse("test:book"); var chapter = ResourceLocation.parse("test:chapter");
        var editing = new QuestScreenFrameIdentity(book, "r1", true, 300, 240);
        var browsing = new QuestScreenFrameIdentity(book, "r1", false, 300, 240);
        var camera = new QuestCanvasRenderer.Camera(0, 0, 1);
        var viewport = new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(0, 0, 300, 240);
        var source = decoration(true).placed(ResourceLocation.parse("test:art"), 0, 0, 4, 2);
        var scene = new CanvasScene(java.util.List.of(source), null, null);
        var controller = new CanvasArtwork();
        var browseFrame = QuestCanvasRenderer.composeFrame(new QuestCanvasRenderer.Model(browsing, chapter, viewport, camera, java.util.List.of(), false, 0, 10, 10));
        controller.prepare(browsing, chapter, scene, camera);
        assertFalse(controller.click(browseFrame, browsing, 10, 10, 0));
        assertFalse(controller.dragging());
        var frame = QuestCanvasRenderer.composeFrame(new QuestCanvasRenderer.Model(editing, chapter, viewport, camera, java.util.List.of(), false, 0, 10, 10));
        controller.prepare(editing, chapter, scene, camera);
        assertTrue(controller.click(frame, editing, 10, 10, 0));
        assertTrue(controller.drag(editing, 44, 44, 0));
        assertNull(controller.release(editing, ResourceLocation.parse("test:other_chapter")));
        assertTrue(controller.click(frame, editing, 10, 10, 0));
        controller.prepare(new QuestScreenFrameIdentity(book, "r1", true, 600, 480), chapter, scene, camera);
        assertFalse(controller.dragging());
        var locked = new CanvasScene.Decoration(source.id(), source.texture(), 0, 0, 4, 2, true, 0, true);
        controller.prepare(editing, chapter, new CanvasScene(java.util.List.of(locked), null, null), camera);
        assertTrue(controller.click(frame, editing, 10, 10, 0));
        assertFalse(controller.dragging());
    }
}

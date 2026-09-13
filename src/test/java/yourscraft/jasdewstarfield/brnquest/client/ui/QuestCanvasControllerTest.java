package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestAppearance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCanvasControllerTest {
    private static final ResourceLocation BOOK = id("book");
    private static final ResourceLocation CHAPTER = id("chapter");
    private static final ResourceLocation A = id("a");
    private static final ResourceLocation B = id("b");
    private static final QuestScreenFrameIdentity IDENTITY =
            new QuestScreenFrameIdentity(BOOK, "rev-1", true, 200, 200);

    @Test
    void chapterFocusAccountsForNavigationWidthAtEverySupportedZoom() {
        for (double zoom : List.of(0.5, 1.0, 2.0)) {
            QuestCanvasController controller = new QuestCanvasController();
            controller.resetCamera(100, -300, zoom);
            controller.focusChapterPoint(408, -272, 360, 300);
            var camera = controller.renderedCamera(300, 180);
            assertEquals(360, camera.screenX(408), 0.00001);
            assertEquals(180, camera.screenY(-272), 0.00001);
            assertEquals(zoom, camera.zoom());
            assertTrue(controller.selection().isEmpty());
            assertFalse(controller.gestureActive());
        }
    }

    @Test
    void viewClickReturnsSemanticDetailsIntentAndStaleFrameIsRejected() {
        QuestCanvasController controller = controller();
        QuestCanvasRenderer.Frame frame = frame(IDENTITY, positions());

        QuestCanvasController.ClickResult click = controller.mouseClicked(frame,
                input(false, IDENTITY), 100, 100, 0, false, 0);
        QuestCanvasController.ClickResult stale = controller.mouseClicked(frame,
                input(false, revision("rev-2")), 100, 100, 0, false, 0);
        QuestCanvasController.ClickResult staleChapter = controller.mouseClicked(frame,
                new QuestCanvasController.InputModel(IDENTITY, id("other_chapter"), false, true, positions()),
                100, 100, 0, false, 0);

        assertTrue(click.consumed());
        assertEquals(QuestCanvasController.Action.OPEN_DETAILS, click.intent().action());
        assertEquals(A, click.intent().targetId());
        assertFalse(stale.consumed());
        assertNull(stale.intent());
        assertFalse(staleChapter.consumed());
    }

    @Test
    void rightClicksReturnNodeAndCanvasContextWithoutMutatingTheBook() {
        QuestCanvasController controller = controller();
        QuestCanvasRenderer.Frame frame = frame(IDENTITY, positions());

        QuestCanvasController.ClickResult node = controller.mouseClicked(frame,
                input(true, IDENTITY), 100, 100, 1, false, 0);
        QuestCanvasController.ClickResult canvas = controller.mouseClicked(frame,
                input(true, IDENTITY), 180, 180, 1, false, 0);

        assertEquals(QuestCanvasController.Action.OPEN_NODE_CONTEXT, node.intent().action());
        assertEquals(A, node.intent().targetId());
        assertEquals(QuestCanvasController.Action.OPEN_CANVAS_CONTEXT, canvas.intent().action());
        assertEquals(80.0 / QuestViewportMath.GRID_SCALE, canvas.intent().questX(), 0.000001);
    }

    @Test
    void shortPressOpensDetailsWhileEarlyTravelBecomesPanAndRestoresSelection() {
        QuestCanvasController controller = controller();
        QuestCanvasRenderer.Frame frame = frame(IDENTITY, positions());
        controller.selectOnly(B);

        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 0);
        QuestCanvasController.GestureResult early = controller.mouseDragged(
                frame, IDENTITY, 110, 100, 0, 100_000_000L);
        QuestCanvasController.GestureResult panRelease = controller.mouseReleased(frame, IDENTITY, 0);

        assertTrue(early.consumed());
        assertEquals(java.util.Set.of(B), controller.selection());
        assertNull(panRelease.intent());

        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 300_000_000L);
        QuestCanvasController.GestureResult shortRelease = controller.mouseReleased(frame, IDENTITY, 0);
        assertEquals(QuestCanvasController.Action.OPEN_DETAILS, shortRelease.intent().action());
        assertEquals(A, shortRelease.intent().targetId());
    }

    @Test
    void longPressMovesMultiSelectionOnceInDeterministicOrder() {
        QuestCanvasController controller = controller();
        QuestCanvasRenderer.Frame frame = frame(IDENTITY, positions());

        // Build A+B selection through the same Ctrl gesture used by the screen.
        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 0);
        controller.mouseReleased(frame, IDENTITY, 0);
        controller.mouseClicked(frame, input(true, IDENTITY), 134, 100, 0, true, 10);
        controller.mouseReleased(frame, IDENTITY, 0);

        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 20);
        controller.mouseDragged(frame, IDENTITY, 134, 100, 0, 300_000_000L);
        QuestCanvasController.GestureResult release = controller.mouseReleased(frame, IDENTITY, 0);
        QuestCanvasController.GestureResult duplicate = controller.mouseReleased(frame, IDENTITY, 0);

        assertEquals(QuestCanvasController.Action.MOVE_QUESTS, release.intent().action());
        assertEquals(List.of(A, B), release.intent().positions().keySet().stream().toList());
        assertEquals(1, release.intent().positions().get(A).x(), 0.000001);
        assertEquals(2, release.intent().positions().get(B).x(), 0.000001);
        assertFalse(duplicate.consumed());
        assertNull(duplicate.intent());
    }

    @Test
    void noMovementAndStaleReleaseNeverEmitPositionIntent() {
        QuestCanvasController controller = controller();
        QuestCanvasRenderer.Frame frame = frame(IDENTITY, positions());

        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 0);
        controller.mouseDragged(frame, IDENTITY, 100, 100, 0, 300_000_000L);
        assertNull(controller.mouseReleased(frame, IDENTITY, 0).intent());

        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 400_000_000L);
        controller.mouseDragged(frame, IDENTITY, 134, 100, 0, 700_000_000L);
        QuestCanvasController.GestureResult stale = controller.mouseReleased(frame, revision("rev-2"), 0);
        assertTrue(stale.consumed());
        assertNull(stale.intent());
    }

    @Test
    void zoomKeepsCenterStableAndFocusDestinationIsNotRewrittenByDrawerMotion() {
        QuestCanvasController controller = controller();
        controller.scrollZoom(1);
        controller.advanceFrame(0.1, 100, 100, disabledFocus());
        QuestCanvasController.StoredViewport zoomed = controller.storedViewport();
        assertEquals(1.1, zoomed.zoom(), 0.0001);
        assertEquals(0, zoomed.centerX(), 0.0001);

        QuestCanvasController.FocusModel first = new QuestCanvasController.FocusModel(
                true, true, A, 34.0, 0.0, 120, 100, 100, 100);
        QuestCanvasController.FocusModel movingDrawer = new QuestCanvasController.FocusModel(
                true, true, A, 34.0, 0.0, 180, 100, 100, 100);
        controller.advanceFrame(0.1, 100, 100, first);
        controller.advanceFrame(0.1, 100, 100, movingDrawer);

        // The first target requires pan=-17.4 at zoom 1.1; a rewritten target would be +42.6.
        assertEquals(17.4 / 1.1, controller.storedViewport().centerX(), 0.02);
    }

    @Test
    void freeDragUsesClickPreferenceAndStillRejectsStaleRelease() {
        var controller = controller();
        var frame = frame(IDENTITY, positions());
        var free = new QuestCanvasController.InputModel(IDENTITY, CHAPTER, true, false, positions());
        controller.mouseClicked(frame, free, 100, 100, 0, false, 0);
        controller.mouseDragged(frame, IDENTITY, 117, 100, 0, 300_000_000L);
        var release = controller.mouseReleased(frame, IDENTITY, 0);
        assertEquals(0.5, release.intent().positions().get(A).x(), 0.000001);
        controller.reconcile(id -> null, true);
        controller.mouseClicked(frame, free, 100, 100, 0, false, 400_000_000L);
        controller.mouseDragged(frame, IDENTITY, 117, 100, 0, 700_000_000L);
        assertNull(controller.mouseReleased(frame, revision("rev-2"), 0).intent());
        assertNull(controller.preview(A));
        // The following gesture samples the enabled preference and returns to whole-grid snapping.
        controller.mouseClicked(frame, input(true, IDENTITY), 100, 100, 0, false, 800_000_000L);
        controller.mouseDragged(frame, IDENTITY, 117, 100, 0, 1_100_000_000L);
        assertEquals(1, controller.mouseReleased(frame, IDENTITY, 0).intent().positions().get(A).x(), 0.000001);
    }

    private static QuestCanvasController controller() {
        QuestCanvasController controller = new QuestCanvasController();
        controller.resetCamera(0, 0, 1);
        return controller;
    }

    private static QuestCanvasController.FocusModel disabledFocus() {
        return new QuestCanvasController.FocusModel(false, false, null, null, null,
                100, 100, 100, 100);
    }

    private static QuestCanvasController.InputModel input(boolean editing, QuestScreenFrameIdentity identity) {
        return new QuestCanvasController.InputModel(identity, CHAPTER, editing, true, positions());
    }

    private static QuestCanvasRenderer.Frame frame(QuestScreenFrameIdentity identity,
                                                    Map<ResourceLocation, DraftBookEditor.Position> positions) {
        List<QuestCanvasRenderer.NodeModel> nodes = positions.entrySet().stream().map(entry ->
                new QuestCanvasRenderer.NodeModel(entry.getKey(), QuestAppearance.DEFAULT, entry.getValue(), null,
                        0, false, false, false, false, false,
                        new QuestPresentation.QuestVisual(QuestPresentation.VisualKind.PLACEHOLDER, ""),
                        ItemStack.EMPTY, List.of(Component.literal(entry.getKey().toString())), List.of())).toList();
        return QuestCanvasRenderer.composeFrame(new QuestCanvasRenderer.Model(identity, CHAPTER,
                new UiRect(0, 0, 200, 200), new QuestCanvasRenderer.Camera(100, 100, 1),
                nodes, false, 0, 0, 0));
    }

    private static Map<ResourceLocation, DraftBookEditor.Position> positions() {
        Map<ResourceLocation, DraftBookEditor.Position> positions = new LinkedHashMap<>();
        positions.put(A, new DraftBookEditor.Position(0, 0));
        positions.put(B, new DraftBookEditor.Position(1, 0));
        return positions;
    }

    private static QuestScreenFrameIdentity revision(String revision) {
        return new QuestScreenFrameIdentity(BOOK, revision, true, 200, 200);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}

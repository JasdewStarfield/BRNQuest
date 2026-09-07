package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestAppearance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCanvasRendererTest {
    private static final ResourceLocation BOOK = id("book");
    private static final ResourceLocation CHAPTER = id("chapter");
    private static final QuestScreenFrameIdentity IDENTITY =
            new QuestScreenFrameIdentity(BOOK, "rev-1", true, 200, 200);

    @Test
    void cameraTransformsBetweenScreenAndGraphCoordinates() {
        QuestCanvasRenderer.Camera camera = new QuestCanvasRenderer.Camera(100, 80, 2.0);

        assertEquals(25, camera.graphX(150), 0.000001);
        assertEquals(10, camera.graphY(100), 0.000001);
        assertEquals(150, camera.screenX(25), 0.000001);
        assertEquals(100, camera.screenY(10), 0.000001);
    }

    @Test
    void frameKeepsPartialNodesAndCrossingDependenciesWhileCullingDistantNodes() {
        ResourceLocation left = id("left"), right = id("right"), partial = id("partial"), distant = id("distant");
        List<QuestCanvasRenderer.NodeModel> nodes = List.of(
                node(left, -4, 0, List.of()),
                node(right, 4, 0, List.of(left)),
                node(partial, -105.0 / QuestViewportMath.GRID_SCALE, 0, List.of()),
                node(distant, 5, 5, List.of()));

        QuestCanvasRenderer.Frame frame = QuestCanvasRenderer.composeFrame(model(nodes));

        assertEquals(List.of(partial), frame.nodes().stream().map(node -> node.model().id()).toList());
        assertEquals(1, frame.dependencies().size(), "An off-screen edge crossing the viewport must remain visible");
        assertEquals(left, frame.dependencies().getFirst().parent().model().id());
        assertEquals(right, frame.dependencies().getFirst().child().model().id());
    }

    @Test
    void nodeHitUsesShapeAndTopmostRenderOrder() {
        ResourceLocation square = id("square"), circle = id("circle");
        QuestCanvasRenderer.NodeModel bottom = node(square, 0, 0, List.of());
        QuestCanvasRenderer.NodeModel top = new QuestCanvasRenderer.NodeModel(circle,
                new QuestAppearance("circle", 1, 1, 0), new DraftBookEditor.Position(0, 0), null,
                0, false, false, false, false, false,
                new QuestPresentation.QuestVisual(QuestPresentation.VisualKind.PLACEHOLDER, ""),
                ItemStack.EMPTY, List.of(Component.literal("circle")), List.of());
        QuestCanvasRenderer.Frame frame = QuestCanvasRenderer.composeFrame(model(List.of(bottom, top)));

        assertEquals(circle, QuestCanvasRenderer.nodeAt(frame, IDENTITY, 100, 100));
        assertFalse(QuestCanvasRenderer.shapeContains("circle", 10, 10, 11));
        assertTrue(QuestCanvasRenderer.shapeContains("square", 10, 10, 11));
        assertFalse(QuestCanvasRenderer.shapeContains("diamond", 7, 7, 11));
    }

    @Test
    void staleOrClippedFrameCannotClaimANode() {
        QuestCanvasRenderer.Frame frame = QuestCanvasRenderer.composeFrame(model(List.of(node(id("node"), 0, 0, List.of()))));

        assertNull(QuestCanvasRenderer.nodeAt(frame,
                new QuestScreenFrameIdentity(BOOK, "rev-2", true, 200, 200), 100, 100));
        assertNull(QuestCanvasRenderer.nodeAt(frame, IDENTITY, -1, 100));
    }

    private static QuestCanvasRenderer.Model model(List<QuestCanvasRenderer.NodeModel> nodes) {
        return new QuestCanvasRenderer.Model(IDENTITY, CHAPTER, new UiRect(0, 0, 200, 200),
                new QuestCanvasRenderer.Camera(100, 100, 1.0), nodes, false, 0, 100, 100);
    }

    private static QuestCanvasRenderer.NodeModel node(ResourceLocation id, double x, double y,
                                                       List<ResourceLocation> dependencies) {
        return new QuestCanvasRenderer.NodeModel(id, QuestAppearance.DEFAULT,
                new DraftBookEditor.Position(x, y), null, 0, false, false, false,
                false, false,
                new QuestPresentation.QuestVisual(QuestPresentation.VisualKind.PLACEHOLDER, ""),
                ItemStack.EMPTY, List.of(Component.literal(id.toString())), dependencies);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}

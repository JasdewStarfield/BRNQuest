package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestNodeGeometry;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestAppearance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders one immutable canvas model and exposes the exact visible geometry used for input. */
final class QuestCanvasRenderer {
    static final int NODE_BASE_SIZE = 18;
    private static final int ATTENTION_PING_SIZE = 10;
    private static final int STATUS_BADGE_SIZE = 10;
    private static final ResourceLocation TRACKED_BADGE = ResourceLocation.parse("brnquest:quest/status/tracked");
    private static final ResourceLocation COMPLETED_BADGE = ResourceLocation.parse("brnquest:quest/status/completed");
    private static final ResourceLocation BLOCKED_BADGE = ResourceLocation.parse("brnquest:quest/status/blocked");
    private static final ResourceLocation REWARD_PING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BRNQuest.MOD_ID, "textures/gui/reward_ping.png");
    private static final ResourceLocation SUBMITTABLE_PING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BRNQuest.MOD_ID, "textures/gui/submitable_ping.png");

    record Camera(double originX, double originY, double zoom) {
        double graphX(double screenX) { return (screenX - originX) / zoom; }
        double graphY(double screenY) { return (screenY - originY) / zoom; }
        double screenX(double graphX) { return originX + graphX * zoom; }
        double screenY(double graphY) { return originY + graphY * zoom; }
    }

    record NodeModel(ResourceLocation id, QuestAppearance appearance,
                     DraftBookEditor.Position position, DraftBookEditor.Position snapPosition,
                     int fillColor, boolean selected, boolean tracked, boolean pickedUp,
                     boolean attentionTask, boolean pendingReward,
                     QuestPresentation.QuestVisual visual, ItemStack item,
                     List<Component> tooltip, List<ResourceLocation> dependencies,
                     yourscraft.jasdewstarfield.brnquest.progress.QuestStatus status) {
        NodeModel(ResourceLocation id, QuestAppearance appearance, DraftBookEditor.Position position,
                  DraftBookEditor.Position snapPosition, int fillColor, boolean selected, boolean tracked,
                  boolean pickedUp, boolean attentionTask, boolean pendingReward,
                  QuestPresentation.QuestVisual visual, ItemStack item, List<Component> tooltip,
                  List<ResourceLocation> dependencies) {
            this(id, appearance, position, snapPosition, fillColor, selected, tracked, pickedUp,
                    attentionTask, pendingReward, visual, item, tooltip, dependencies, null);
        }
        NodeModel {
            appearance = appearance == null ? QuestAppearance.DEFAULT : appearance;
            item = item == null ? ItemStack.EMPTY : item.copy();
            tooltip = List.copyOf(tooltip);
            dependencies = List.copyOf(dependencies);
        }
    }

    record Model(QuestScreenFrameIdentity identity, ResourceLocation chapterId, UiRect viewport,
                 Camera camera, List<NodeModel> nodes, boolean dragActive, int attentionPingOffsetY,
                 double pointerX, double pointerY) {
        Model {
            nodes = List.copyOf(nodes);
        }
    }

    record NodeFrame(NodeModel model, int graphX, int graphY, int visualSize, int hitRadius) {}
    record DependencyFrame(NodeFrame parent, NodeFrame child) {}
    record GraphBounds(double left, double right, double top, double bottom) {}
    record Frame(QuestScreenFrameIdentity identity, ResourceLocation chapterId, UiRect viewport,
                 Camera camera, GraphBounds graphBounds, List<NodeFrame> nodes,
                 List<DependencyFrame> dependencies) {
        Frame {
            nodes = List.copyOf(nodes);
            dependencies = List.copyOf(dependencies);
        }

        boolean matches(QuestScreenFrameIdentity current) {
            return identity != null && identity.equals(current);
        }
    }

    record RenderResult(Frame frame, List<Component> tooltip) {
        RenderResult {
            tooltip = List.copyOf(tooltip);
        }
    }

    RenderResult render(GuiGraphics graphics, Font font, Model model) {
        Frame frame = composeFrame(model);
        if (frame.viewport().width() <= 0 || frame.viewport().height() <= 0) {
            return new RenderResult(frame, List.of());
        }

        graphics.enableScissor(frame.viewport().left(), frame.viewport().top(),
                frame.viewport().right(), frame.viewport().bottom());
        graphics.pose().pushPose();
        graphics.pose().translate((float) frame.camera().originX(), (float) frame.camera().originY(), 0.0F);
        graphics.pose().scale((float) frame.camera().zoom(), (float) frame.camera().zoom(), 1.0F);
        renderGrid(graphics, frame.graphBounds());
        frame.dependencies().forEach(dependency -> renderDependency(graphics, dependency));
        frame.nodes().forEach(node -> renderSnapGhost(graphics, font, node, frame.graphBounds()));
        frame.nodes().forEach(node -> renderNode(graphics, font, node, model.attentionPingOffsetY()));
        graphics.pose().popPose();
        graphics.disableScissor();

        ResourceLocation hovered = model.dragActive() ? null
                : nodeAt(frame, model.identity(), model.pointerX(), model.pointerY());
        return new RenderResult(frame, hovered == null ? List.of() : frame.nodes().stream()
                .filter(node -> node.model().id().equals(hovered))
                .findFirst().map(node -> node.model().tooltip()).orElse(List.of()));
    }

    /** Builds culling and hit geometry without a graphics context so it stays deterministic in tests. */
    static Frame composeFrame(Model model) {
        GraphBounds bounds = new GraphBounds(
                model.camera().graphX(model.viewport().left()),
                model.camera().graphX(model.viewport().right()),
                model.camera().graphY(model.viewport().top()),
                model.camera().graphY(model.viewport().bottom()));
        List<NodeFrame> visibleNodes = new ArrayList<>();
        Map<ResourceLocation, NodeFrame> allNodes = new LinkedHashMap<>();
        for (NodeModel node : model.nodes()) {
            int x = graphCoordinate(node.position().x());
            int y = graphCoordinate(node.position().y());
            int normalSize = QuestNodeGeometry.visualSize(NODE_BASE_SIZE, node.appearance());
            int visualSize = normalSize + (node.pickedUp() ? 4 : 0);
            NodeFrame frame = new NodeFrame(node, x, y, visualSize,
                    QuestNodeGeometry.hitRadius(NODE_BASE_SIZE, node.appearance()));
            allNodes.put(node.id(), frame);
            if (QuestViewportMath.intersectsViewport(x, y, visualSize / 2,
                    bounds.left(), bounds.right(), bounds.top(), bounds.bottom())) {
                visibleNodes.add(frame);
            }
        }

        List<DependencyFrame> dependencies = new ArrayList<>();
        for (NodeModel child : model.nodes()) {
            NodeFrame childFrame = allNodes.get(child.id());
            for (ResourceLocation dependencyId : child.dependencies()) {
                NodeFrame parentFrame = allNodes.get(dependencyId);
                if (parentFrame != null && dependencyMayBeVisible(parentFrame, childFrame, bounds)) {
                    dependencies.add(new DependencyFrame(parentFrame, childFrame));
                }
            }
        }
        return new Frame(model.identity(), model.chapterId(), model.viewport(), model.camera(), bounds,
                visibleNodes, dependencies);
    }

    /** Returns the topmost visible node only when this geometry belongs to the current screen frame. */
    static ResourceLocation nodeAt(Frame frame, QuestScreenFrameIdentity current,
                                   double screenX, double screenY) {
        if (frame == null || !frame.matches(current) || !frame.viewport().containsExclusive(screenX, screenY)) {
            return null;
        }
        double graphX = frame.camera().graphX(screenX);
        double graphY = frame.camera().graphY(screenY);
        for (int index = frame.nodes().size() - 1; index >= 0; index--) {
            NodeFrame node = frame.nodes().get(index);
            if (shapeContains(node.model().appearance().shape(), graphX - node.graphX(),
                    graphY - node.graphY(), node.hitRadius())) {
                return node.model().id();
            }
        }
        return null;
    }

    static boolean shapeContains(String shape, double dx, double dy, int radius) {
        String normalized = shape == null ? "" : shape.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "circle" -> dx * dx + dy * dy <= radius * radius;
            case "diamond" -> Math.abs(dx) + Math.abs(dy) <= radius;
            case "square" -> Math.abs(dx) <= radius && Math.abs(dy) <= radius;
            default -> {
                int cut = Math.max(1, (radius * 2) / 6);
                yield Math.abs(dx) <= radius && Math.abs(dy) <= radius
                        && (Math.abs(dx) <= radius - cut || Math.abs(dy) <= radius - cut);
            }
        };
    }

    private static boolean dependencyMayBeVisible(NodeFrame first, NodeFrame second, GraphBounds bounds) {
        double margin = NODE_BASE_SIZE;
        return !(first.graphX() < bounds.left() - margin && second.graphX() < bounds.left() - margin)
                && !(first.graphX() > bounds.right() + margin && second.graphX() > bounds.right() + margin)
                && !(first.graphY() < bounds.top() - margin && second.graphY() < bounds.top() - margin)
                && !(first.graphY() > bounds.bottom() + margin && second.graphY() > bounds.bottom() + margin);
    }

    private static void renderGrid(GuiGraphics graphics, GraphBounds bounds) {
        int grid = (int) QuestViewportMath.GRID_SCALE;
        int firstX = (int) Math.floor(bounds.left() / grid) * grid;
        int firstY = (int) Math.floor(bounds.top() / grid) * grid;
        int drawLeft = (int) Math.floor(bounds.left()) - 1;
        int drawRight = (int) Math.ceil(bounds.right()) + 1;
        int drawTop = (int) Math.floor(bounds.top()) - 1;
        int drawBottom = (int) Math.ceil(bounds.bottom()) + 1;
        for (int x = firstX; x <= drawRight; x += grid) graphics.fill(x, drawTop, x + 1, drawBottom, 0x243C4655);
        for (int y = firstY; y <= drawBottom; y += grid) graphics.fill(drawLeft, y, drawRight, y + 1, 0x243C4655);
    }

    /** Draws an orthogonal dependency path whose arrow always points at the dependent node. */
    private static void renderDependency(GuiGraphics graphics, DependencyFrame dependency) {
        int x1 = dependency.parent().graphX(), y1 = dependency.parent().graphY();
        int x2 = dependency.child().graphX(), y2 = dependency.child().graphY();
        int radius = NODE_BASE_SIZE / 2;
        int color = 0xC0798799;
        if (Math.abs(x2 - x1) < radius * 2) {
            int direction = y2 >= y1 ? 1 : -1;
            int startY = y1 + direction * radius;
            int endY = y2 - direction * radius;
            vertical(graphics, x1, startY, endY, color);
            arrowVertical(graphics, x1, endY, direction, color);
            return;
        }
        int direction = x2 >= x1 ? 1 : -1;
        int startX = x1 + direction * radius;
        int endX = x2 - direction * radius;
        int middleX = (startX + endX) / 2;
        horizontal(graphics, startX, middleX, y1, color);
        vertical(graphics, middleX, y1, y2, color);
        horizontal(graphics, middleX, endX, y2, color);
        arrowHorizontal(graphics, endX, y2, direction, color);
    }

    private static void renderNode(GuiGraphics graphics, Font font, NodeFrame node, int pingOffsetY) {
        NodeModel model = node.model();
        int x = node.graphX(), y = node.graphY(), size = node.visualSize();
        if (model.tracked()) fillNodeShape(graphics, model.appearance().shape(), x, y, size + 7, 0xFF57C7F2);
        fillNodeShape(graphics, model.appearance().shape(), x, y, size + (model.selected() ? 4 : 2),
                model.selected() ? 0xFFE4D29A : 0xFF22251F);
        fillNodeShape(graphics, model.appearance().shape(), x, y, size, model.fillColor());
        renderQuestVisual(graphics, font, model, x, y, size);
        ResourceLocation badge = statusBadge(model.status());
        if (badge != null) {
            // Native item models write depth above the base GUI. Lift the entire badge above them,
            // and anchor mostly outside the lower-left corner without enlarging the node's hitbox.
            UiRect bounds = statusBadgeBounds(x, y, size);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 300);
                graphics.blitSprite(badge, bounds.left(), bounds.top(), bounds.width(), bounds.height());
            } finally {
                graphics.pose().popPose();
            }
        }
        if (model.attentionTask()) renderAttentionPing(graphics, SUBMITTABLE_PING_TEXTURE, x, y, size, pingOffsetY);
        if (model.pendingReward()) renderAttentionPing(graphics, REWARD_PING_TEXTURE, x, y, size, pingOffsetY);
    }

    static UiRect statusBadgeBounds(int x, int y, int size) {
        int left = x - size / 2 - STATUS_BADGE_SIZE + 2;
        int top = y + size / 2 - 2;
        return new UiRect(left, top, left + STATUS_BADGE_SIZE, top + STATUS_BADGE_SIZE);
    }

    static ResourceLocation statusBadge(yourscraft.jasdewstarfield.brnquest.progress.QuestStatus status) {
        if (status == null) return null;
        return switch (status) {
            case LOCKED, UNAVAILABLE -> BLOCKED_BADGE;
            case ACTIVE -> TRACKED_BADGE;
            case COMPLETED, REWARD_CLAIMED -> COMPLETED_BADGE;
            case AVAILABLE -> null;
        };
    }

    private static void renderSnapGhost(GuiGraphics graphics, Font font, NodeFrame node, GraphBounds bounds) {
        DraftBookEditor.Position target = node.model().snapPosition();
        // Every selected member has a snapped target; only the anchor receives the enlarged live visual.
        if (target == null) return;
        int x = graphCoordinate(target.x()), y = graphCoordinate(target.y());
        int radius = NODE_BASE_SIZE / 2 + 3;
        if (!QuestViewportMath.intersectsViewport(x, y, radius,
                bounds.left(), bounds.right(), bounds.top(), bounds.bottom())) return;
        fillChamfer(graphics, x, y, NODE_BASE_SIZE + 6, 0x9091C9F4);
        fillChamfer(graphics, x, y, NODE_BASE_SIZE + 2, 0xB0202632);
        graphics.drawCenteredString(font, Component.literal("◇"), x, y - 4, 0xD091C9F4);
    }

    private static void renderQuestVisual(GuiGraphics graphics, Font font, NodeModel node, int x, int y, int size) {
        QuestPresentation.QuestVisual visual = node.visual();
        if (visual.icon().isPresent()) {
            float scale = Math.max(0.25F, (float) (size / 18.0F * safeScale(node.appearance().iconScale())));
            graphics.pose().pushPose();
            graphics.pose().translate(x - 8.0F * scale, y - 8.0F * scale, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            visual.icon().orElseThrow().render(graphics,font,new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(0,0,16,16),0xFFFFFFFF);
            graphics.pose().popPose();
            return;
        }
        if (visual.kind() == QuestPresentation.VisualKind.TEXTURE) {
            int iconSize = Math.max(1, (int) Math.round(size * safeScale(node.appearance().iconScale())));
            yourscraft.jasdewstarfield.brnquest.data.QuestIconValue.textureId(visual.value()).ifPresent(texture ->
                    graphics.blit(texture, x - iconSize / 2, y - iconSize / 2, 0.0F, 0.0F,
                            iconSize, iconSize, iconSize, iconSize));
            return;
        }
        if (visual.kind() == QuestPresentation.VisualKind.ITEM && !node.item().isEmpty()) {
            float scale = Math.max(0.25F, (float) (size / 18.0F * safeScale(node.appearance().iconScale())));
            graphics.pose().pushPose();
            graphics.pose().translate(x - 8.0F * scale, y - 8.0F * scale, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(node.item(), 0, 0);
            graphics.pose().popPose();
            return;
        }
        String symbol = switch (visual.kind()) {
            case CHECKMARK -> "✓";
            case CUSTOM -> "◆";
            default -> "?";
        };
        float scale = Math.max(0.5F, (float) (size / 18.0F * safeScale(node.appearance().iconScale())));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, symbol, 0, -font.lineHeight / 2, 0xFFFFFFFF);
        graphics.pose().popPose();
    }

    private static void renderAttentionPing(GuiGraphics graphics, ResourceLocation texture,
                                            int x, int y, int size, int offsetY) {
        int radius = size / 2;
        QuestDetailRows.renderAttentionPing(graphics, texture,
                x + radius - 2, y - radius - ATTENTION_PING_SIZE + 2, offsetY);
    }

    private static int graphCoordinate(double coordinate) {
        return (int) Math.round(coordinate * QuestViewportMath.GRID_SCALE);
    }

    private static double safeScale(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    /** Unknown imported shapes retain the existing safe chamfer fallback. */
    private static void fillNodeShape(GuiGraphics graphics, String shape, int x, int y, int size, int color) {
        int radius = size / 2;
        switch (shape.toLowerCase(Locale.ROOT)) {
            case "square" -> graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
            case "circle" -> {
                for (int dy = -radius; dy <= radius; dy++) {
                    int half = (int) Math.floor(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
                    graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
                }
            }
            case "diamond" -> {
                for (int dy = -radius; dy <= radius; dy++) {
                    int half = radius - Math.abs(dy);
                    graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
                }
            }
            default -> fillChamfer(graphics, x, y, size, color);
        }
    }

    private static void fillChamfer(GuiGraphics graphics, int x, int y, int size, int color) {
        int radius = size / 2;
        int cut = Math.max(1, size / 6);
        graphics.fill(x - radius + cut, y - radius, x + radius - cut + 1, y + radius + 1, color);
        graphics.fill(x - radius, y - radius + cut, x + radius + 1, y + radius - cut + 1, color);
    }

    private static void horizontal(GuiGraphics graphics, int x1, int x2, int y, int color) {
        graphics.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, color);
    }

    private static void vertical(GuiGraphics graphics, int x, int y1, int y2, int color) {
        graphics.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, color);
    }

    private static void arrowHorizontal(GuiGraphics graphics, int x, int y, int direction, int color) {
        for (int offset = 0; offset <= 4; offset++) {
            int half = Math.max(0, offset / 2);
            int px = x - direction * offset;
            graphics.fill(px, y - half, px + 1, y + half + 1, color);
        }
    }

    private static void arrowVertical(GuiGraphics graphics, int x, int y, int direction, int color) {
        for (int offset = 0; offset <= 4; offset++) {
            int half = Math.max(0, offset / 2);
            int py = y - direction * offset;
            graphics.fill(x - half, py, x + half + 1, py + 1, color);
        }
    }
}

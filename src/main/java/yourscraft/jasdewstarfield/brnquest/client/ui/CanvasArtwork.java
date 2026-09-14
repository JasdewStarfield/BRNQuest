package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import java.util.*;

/** Renders artwork and owns single-item resizing; the canvas controller owns mixed selection/movement. */
final class CanvasArtwork {
    private QuestScreenFrameIdentity identity;
    private ResourceLocation chapter, selected;
    private CanvasScene scene = CanvasScene.EMPTY;
    private CanvasScene.Decoration original, preview;
    private QuestCanvasRenderer.Camera camera;
    private double startX, startY;
    private boolean resizing, snap;
    private QuestCanvasController selectionController;
    void selectionController(QuestCanvasController controller) { selectionController = controller; }
    static CanvasScene.Decoration hit(List<CanvasScene.Decoration> decorations, QuestCanvasRenderer.Camera camera, double x, double y) {
        double gx = camera.graphX(x) / QuestViewportMath.GRID_SCALE, gy = camera.graphY(y) / QuestViewportMath.GRID_SCALE;
        return decorations.stream().filter(d -> gx >= d.x() && gy >= d.y() && gx <= d.x()+d.width() && gy <= d.y()+d.height())
                .max(Comparator.comparingInt(CanvasScene.Decoration::layer).thenComparing(d -> d.id().toString())).orElse(null);
    }
    /** Only an already selected, unlocked single item exposes its resize handle. */
    static boolean resizeHandle(CanvasScene.Decoration d, QuestCanvasRenderer.Camera camera, double x, double y) {
        double gx = camera.graphX(x) / QuestViewportMath.GRID_SCALE, gy = camera.graphY(y) / QuestViewportMath.GRID_SCALE;
        return !d.locked() && (d.x()+d.width()-gx)*QuestViewportMath.GRID_SCALE < 8
                && (d.y()+d.height()-gy)*QuestViewportMath.GRID_SCALE < 8;
    }

    void render(GuiGraphics g, QuestScreenFrameIdentity frame, ResourceLocation chapterId, CanvasScene value,
                CanvasScene book, QuestCanvasRenderer.Camera currentCamera, UiRect viewport) {
        prepare(frame, chapterId, value, currentCamera);
        // Each coordinate space owns a complete clip/pose scope. A nested full-screen clip
        // cannot replace the canvas clip: GuiGraphics pushes it and intersects with its parent.
        canvasLayer(g, camera, viewport, () -> background(g, CanvasScene.effective(value.canvas(), book.canvas()),
                -512, -512, 1024, 1024, camera.graphX(viewport.left()), camera.graphY(viewport.top()),
                camera.graphX(viewport.right()), camera.graphY(viewport.bottom())));
        if (value.screenAbove(book))
            screenBackground(g, CanvasScene.effective(value.screen(), book.screen()), frame.width(), frame.height());
        canvasLayer(g, camera, viewport, () -> {
            for (var source : ordered(value)) {
                var d = preview != null && preview.id().equals(source.id()) ? preview : source;
                var position = selectionController == null ? null : selectionController.decorationPreview(d.id());
                if (position != null) d = d.placed(d.id(), position.x(), position.y(), d.width(), d.height());
                int x = px(d.x()), y = px(d.y()), w = Math.max(1, px(d.width())), h = Math.max(1, px(d.height()));
                if (x + w < camera.graphX(viewport.left()) || x > camera.graphX(viewport.right())
                        || y + h < camera.graphY(viewport.top()) || y > camera.graphY(viewport.bottom())) continue;
                LoadedTextures.draw(g, d.texture(), x, y, w, h, 1);
                if (frame.editing() && (selectionController == null ? d.id().equals(selected) : selectionController.decorationSelected(d.id()))) {
                    g.renderOutline(x, y, w, h, d.locked() ? 0xFF9C9C9C : 0xFFE4C272);
                    if (!d.locked() && (selectionController == null || selectionController.selectionCount() == 1)) g.fill(x + w - 6, y + h - 6, x + w, y + h, 0xFFE4C272);
                }
            }
        });
    }
    /** Always unwind the exact state we push, including when a resource renderer fails. */
    static void canvasLayer(GuiGraphics g, QuestCanvasRenderer.Camera camera, UiRect viewport, Runnable draw) {
        g.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        try {
            g.pose().pushPose();
            try {
                g.pose().translate((float)camera.originX(), (float)camera.originY(), 0);
                g.pose().scale((float)camera.zoom(), (float)camera.zoom(), 1);
                draw.run();
            } finally { g.pose().popPose(); }
        } finally { g.disableScissor(); }
    }
    /** Frame binding is independent of rendering so stale gestures can be verified headlessly. */
    void prepare(QuestScreenFrameIdentity frame, ResourceLocation chapterId, CanvasScene value, QuestCanvasRenderer.Camera currentCamera) {
        if (!Objects.equals(identity, frame) || !Objects.equals(chapter, chapterId)) {
            original = null; preview = null;
            if (!Objects.equals(chapter, chapterId) || identity == null || !identity.bookId().equals(frame.bookId())) selected = null;
        }
        identity = frame; chapter = chapterId; scene = value; camera = currentCamera;
        if (value.decorations().stream().noneMatch(d -> d.id().equals(selected))) selected = null;
    }
    private static int px(double grid) { return (int)Math.round(grid * QuestViewportMath.GRID_SCALE); }
    private static List<CanvasScene.Decoration> ordered(CanvasScene scene) {
        return scene.decorations().stream().sorted(Comparator.comparingInt(CanvasScene.Decoration::layer).thenComparing(d -> d.id().toString())).toList();
    }
    static void screenBackground(GuiGraphics g, CanvasScene.Background b, int width, int height) {
        g.enableScissor(0, 0, width, height);
        try { background(g, b, 0, 0, width, height, 0, 0, width, height); }
        finally { g.disableScissor(); }
    }
    private static void background(GuiGraphics g, CanvasScene.Background b, int x, int y, int w, int h,
                                   double visibleLeft, double visibleTop, double visibleRight, double visibleBottom) {
        if (b == null || b.texture().isEmpty() || b.opacity() == 0) return;
        var size = LoadedTextures.size(ResourceLocation.parse(b.texture()));
        if (b.fit() == CanvasScene.Fit.TILE) {
            // Stable 128-pixel cells bound draw work even for a resource that is only one pixel wide.
            // A bounded multiplier keeps tile count finite while preserving the stable origin.
            int tile = (int)Math.round(128 * b.scale());
            for (int tx = (int)Math.floor(visibleLeft / tile) * tile; tx < visibleRight; tx += tile)
                for (int ty = (int)Math.floor(visibleTop / tile) * tile; ty < visibleBottom; ty += tile)
                    LoadedTextures.draw(g, b.texture(), tx, ty, tile, tile, b.opacity());
        } else {
            double scale = b.fit() == CanvasScene.Fit.CONTAIN ? Math.min((double)w/size.width(), (double)h/size.height())
                    : Math.max((double)w/size.width(), (double)h/size.height());
            scale *= b.scale();
            int dw = Math.max(1, (int)Math.round(size.width()*scale)), dh = Math.max(1, (int)Math.round(size.height()*scale));
            // Clip cover at its authored area, using UV cropping rather than a second nested scissor.
            if (b.fit() == CanvasScene.Fit.COVER && size.present() && dw >= w && dh >= h) {
                var id = ResourceLocation.parse(b.texture());
                int sw = Math.max(1, (int)Math.round(w/scale)), sh = Math.max(1, (int)Math.round(h/scale));
                com.mojang.blaze3d.systems.RenderSystem.enableBlend(); g.setColor(1,1,1,(float)b.opacity());
                try { g.blit(id, x, y, w, h, (size.width()-sw)/2F, (size.height()-sh)/2F, sw, sh, size.width(), size.height()); }
                finally { g.setColor(1,1,1,1); com.mojang.blaze3d.systems.RenderSystem.disableBlend(); }
            } else LoadedTextures.draw(g, b.texture(), x + (w-dw)/2, y + (h-dh)/2, dw, dh, b.opacity());
        }
    }
    boolean click(QuestCanvasRenderer.Frame frame, QuestScreenFrameIdentity current, double x, double y, int button) {
        if (identity == null || !identity.equals(current) || !current.editing() || frame == null || !frame.matches(current)) return false;
        // Tasks always retain priority, including when artwork geometrically overlaps them.
        if (QuestCanvasRenderer.nodeAt(frame, current, x, y) != null) { selected = null; return false; }
        if (button != 0 && button != 1) return false;
        var values = ordered(scene);
        double gx = camera.graphX(x) / QuestViewportMath.GRID_SCALE, gy = camera.graphY(y) / QuestViewportMath.GRID_SCALE;
        for (int i = values.size() - 1; i >= 0; i--) {
            var d = values.get(i);
            if (gx < d.x() || gy < d.y() || gx > d.x()+d.width() || gy > d.y()+d.height()) continue;
            if (button == 1) cancel();
            selected = d.id();
            // Right-click selects even locked artwork, but must never begin a drag.
            if (button == 0 && !d.locked()) {
                original = d; preview = d; startX = gx; startY = gy;
                resizing = (d.x()+d.width()-gx)*QuestViewportMath.GRID_SCALE < 8 && (d.y()+d.height()-gy)*QuestViewportMath.GRID_SCALE < 8;
                snap = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.snapToGrid);
            }
            return true;
        }
        selected = null; return false;
    }
    boolean drag(QuestScreenFrameIdentity current, double x, double y, int button) {
        if (original == null || button != 0) return false;
        if (!Objects.equals(identity, current)) { cancel(); return true; }
        double dx = camera.graphX(x)/QuestViewportMath.GRID_SCALE-startX, dy = camera.graphY(y)/QuestViewportMath.GRID_SCALE-startY;
        preview = transformed(original, dx, dy, resizing, snap);
        return true;
    }
    /** Pure geometry is shared by the visible drag preview and the eventual immutable commit. */
    static CanvasScene.Decoration transformed(CanvasScene.Decoration d, double dx, double dy, boolean resize, boolean snap) {
        if (!resize) {
            double x = d.x()+dx, y = d.y()+dy;
            if (snap) { x = QuestViewportMath.snapQuestCoordinate(x); y = QuestViewportMath.snapQuestCoordinate(y); }
            return d.placed(d.id(), Math.clamp(x, -1_000_000, 1_000_000), Math.clamp(y, -1_000_000, 1_000_000), d.width(), d.height());
        }
        double w = Math.clamp(d.width()+dx, 0.05, 1024), h = Math.clamp(d.height()+dy, 0.05, 1024);
        if (snap) { w = Math.max(0.05, Math.round(w)); h = Math.max(0.05, Math.round(h)); }
        if (d.aspectLocked()) {
            double scale = Math.clamp(Math.max(w/d.width(), h/d.height()),
                    Math.max(0.05/d.width(), 0.05/d.height()), Math.min(1024/d.width(), 1024/d.height()));
            w = d.width()*scale; h = d.height()*scale;
        }
        return d.placed(d.id(), d.x(), d.y(), w, h);
    }
    CanvasScene.Decoration selected(QuestScreenFrameIdentity current, ResourceLocation currentChapter) {
        if (!Objects.equals(identity, current) || !Objects.equals(chapter, currentChapter)) return null;
        return scene.decorations().stream().filter(d -> d.id().equals(selected)).findFirst().orElse(null);
    }
    boolean dragging() { return original != null; }
    boolean cancel() { boolean active = original != null; original = null; preview = null; return active; }
    CanvasScene release(QuestScreenFrameIdentity current, ResourceLocation currentChapter) {
        if (original == null) return null;
        var replacement = preview; var before = original; cancel();
        if (!Objects.equals(current, identity) || !Objects.equals(chapter, currentChapter) || before.equals(replacement)) return null;
        return new CanvasScene(scene.decorations().stream().map(d -> d.id().equals(replacement.id()) ? replacement : d).toList(), scene.canvas(), scene.screen(), scene.screenAbove());
    }
}

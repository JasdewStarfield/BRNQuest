package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSelectionFocus;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothValue;

import yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns canvas camera and pointer gestures while returning protocol-free semantic intents. */
final class QuestCanvasController {
    enum Action { OPEN_DETAILS, OPEN_NODE_CONTEXT, OPEN_CANVAS_CONTEXT, OPEN_DECORATION_CONTEXT, MOVE_QUESTS, MOVE_CANVAS_SELECTION }

    record Intent(Action action, ResourceLocation targetId, int pointerX, int pointerY,
                  double questX, double questY, Map<ResourceLocation, DraftBookEditor.Position> positions) {
        Intent {
            positions = Collections.unmodifiableMap(new LinkedHashMap<>(positions));
        }

        static Intent details(ResourceLocation id) {
            return new Intent(Action.OPEN_DETAILS, id, 0, 0, 0, 0, Map.of());
        }

        static Intent context(Action action, ResourceLocation id, double pointerX, double pointerY,
                              double questX, double questY) {
            return new Intent(action, id, (int) pointerX, (int) pointerY, questX, questY, Map.of());
        }

        static Intent move(Map<ResourceLocation, DraftBookEditor.Position> positions) {
            boolean mixed = positions.keySet().stream().anyMatch(CanvasSelectionKey::isDecoration);
            Map<ResourceLocation, DraftBookEditor.Position> values = new LinkedHashMap<>();
            positions.forEach((key, value) -> values.put(mixed ? key : CanvasSelectionKey.id(key), value));
            return new Intent(mixed ? Action.MOVE_CANVAS_SELECTION : Action.MOVE_QUESTS, null, 0, 0, 0, 0, values);
        }
    }

    record InputModel(QuestScreenFrameIdentity identity, ResourceLocation chapterId, boolean editing, boolean snapToGrid,
                      Map<ResourceLocation, DraftBookEditor.Position> chapterPositions, java.util.List<CanvasScene.Decoration> decorations) {
        InputModel(QuestScreenFrameIdentity identity, ResourceLocation chapterId, boolean editing, boolean snapToGrid,
                   Map<ResourceLocation, DraftBookEditor.Position> chapterPositions) {
            this(identity, chapterId, editing, snapToGrid, chapterPositions, java.util.List.of());
        }
        InputModel {
            chapterPositions = Collections.unmodifiableMap(new LinkedHashMap<>(chapterPositions));
            decorations = java.util.List.copyOf(decorations);
        }
    }

    record ClickResult(boolean consumed, Intent intent) {}
    record GestureResult(boolean consumed, Intent intent) {}
    record FocusModel(boolean enabled, boolean detailsOpen, ResourceLocation selectedId,
                      Double targetGraphX, Double targetGraphY, double targetScreenX,
                      double targetScreenY, double screenOriginX, double screenOriginY) {}
    record StoredViewport(double centerX, double centerY, double zoom) {}

    private double panX;
    private double panY;
    private double zoom = 1.0;
    private double renderedPanX;
    private double renderedPanY;
    private double renderedZoom = 1.0;
    private double dragX;
    private double dragY;
    private boolean panning;
    private QuestScreenFrameIdentity gestureIdentity;
    private final EditorSmoothValue zoomMotion = new EditorSmoothValue(1.0, 0.00001);
    private final EditorSelectionFocus<ResourceLocation> selectionFocus = new EditorSelectionFocus<>();
    private ResourceLocation explicitFocusId;
    private boolean explicitFocusPending;

    /** Explicit links use the same easing as node selection, even when automatic focus is disabled. */
    void requestFocus(ResourceLocation id) {
        explicitFocusId = Objects.requireNonNull(id);
        explicitFocusPending = true;
    }
    private final QuestNodeDrag nodeDrag = new QuestNodeDrag();
    private final Set<ResourceLocation> selection = new LinkedHashSet<>();

    QuestCanvasRenderer.Camera renderedCamera(double screenOriginX, double screenOriginY) {
        return new QuestCanvasRenderer.Camera(screenOriginX + renderedPanX,
                screenOriginY + renderedPanY, renderedZoom);
    }

    // Public quest accessors preserve the renderer contract; gesture state uses typed keys throughout.
    Set<ResourceLocation> selection() { return CanvasSelectionKey.ids(selection, false); }
    Set<ResourceLocation> allSelection() { return Set.copyOf(selection); }
    Set<ResourceLocation> decorationSelection() { return CanvasSelectionKey.ids(selection, true); }
    int selectionCount() { return selection.size(); }
    boolean selected(ResourceLocation id) { return selection.contains(CanvasSelectionKey.quest(id)); }
    boolean decorationSelected(ResourceLocation id) { return selection.contains(CanvasSelectionKey.decoration(id)); }
    private boolean selectionOnlyClick;
    boolean gestureActive() { return panning || nodeDrag.active(); }
    boolean dragActive() { return nodeDrag.active(); }
    boolean pickedUp() { return nodeDrag.pickedUp(); }
    ResourceLocation dragAnchor() { return nodeDrag.anchor() == null || CanvasSelectionKey.isDecoration(nodeDrag.anchor()) ? null : CanvasSelectionKey.id(nodeDrag.anchor()); }
    DraftBookEditor.Position preview(ResourceLocation id) { return nodeDrag.preview(CanvasSelectionKey.quest(id)); }
    DraftBookEditor.Position snapPreview(ResourceLocation id) { return nodeDrag.snapPreview(CanvasSelectionKey.quest(id)); }

    /** A stationary long press may cross its threshold between input events, so render frames advance it too. */
    void advancePointer(QuestCanvasRenderer.Camera camera, double screenX, double screenY, long nowNanos) {
        nodeDrag.update(camera.graphX(screenX), camera.graphY(screenY), screenX, screenY, nowNanos);
    }

    DraftBookEditor.Position decorationPreview(ResourceLocation id) { return nodeDrag.preview(CanvasSelectionKey.decoration(id)); }
    /** Rollback restores only client selection; authoritative reconciliation still prunes missing objects. */
    void restoreSelection(Set<ResourceLocation> keys) { selection.clear(); selection.addAll(keys); }
    void clearSelection() { selection.clear(); }
    void removeSelection(ResourceLocation id) { selection.remove(CanvasSelectionKey.quest(id)); }
    void clearPreview() { nodeDrag.clearPreview(); }

    void resetCamera(double graphCenterX, double graphCenterY, double zoom) {
        this.zoom = QuestViewportMath.clampZoom(zoom);
        zoomMotion.snap(this.zoom);
        renderedZoom = this.zoom;
        panX = QuestViewportMath.panForGraphCenter(graphCenterX, this.zoom);
        panY = QuestViewportMath.panForGraphCenter(graphCenterY, this.zoom);
        renderedPanX = panX;
        renderedPanY = panY;
        selectionFocus.reset(panX, panY);
        explicitFocusId = null;
        explicitFocusPending = false;
        selection.clear();
        cancelGesture();
    }

    /** Centers a chapter entry point inside the visible canvas without changing zoom or selecting it. */
    void focusChapterPoint(double graphX, double graphY, double targetScreenX, double screenOriginX) {
        resetCamera(graphX - (targetScreenX - screenOriginX) / zoom, graphY, zoom);
    }

    void resetChapter() {
        clearSelection();
        nodeDrag.clearPreview();
        zoomMotion.snap(renderedZoom);
        zoom = renderedZoom;
        panX = panY = renderedPanX = renderedPanY = 0;
        selectionFocus.reset(0, 0);
        explicitFocusId = null;
        explicitFocusPending = false;
        cancelGesture();
    }

    StoredViewport storedViewport() {
        return new StoredViewport(QuestViewportMath.graphCenterForPan(panX, zoom),
                QuestViewportMath.graphCenterForPan(panY, zoom), zoom);
    }

    /** Advances camera easing using render-frame time; focus destination is sampled once by EditorSelectionFocus. */
    void advanceFrame(double seconds, double zoomSpeed, double focusSpeed, FocusModel focus) {
        double oldZoom = zoom;
        zoom = zoomMotion.advanceFrame(seconds, zoomSpeed);
        if (Double.compare(oldZoom, zoom) != 0) {
            panX = QuestViewportMath.panForStableAnchor(focus.screenOriginX(), focus.screenOriginX(), panX, oldZoom, zoom);
            panY = QuestViewportMath.panForStableAnchor(focus.screenOriginY(), focus.screenOriginY(), panY, oldZoom, zoom);
        }
        renderedZoom = zoom;
        renderedPanX = panX;
        renderedPanY = panY;

        boolean requested = selectionFocus.observe(focus.detailsOpen(), focus.selectedId());
        boolean explicit = explicitFocusId != null && explicitFocusId.equals(focus.selectedId());
        if ((!focus.enabled() && !explicit) || !focus.detailsOpen() || focus.selectedId() == null
                || focus.targetGraphX() == null || focus.targetGraphY() == null) {
            cancelFocus();
            return;
        }
        if (requested || explicit && explicitFocusPending) {
            finishZoomMotion(focus.screenOriginX(), focus.screenOriginY());
            selectionFocus.start(focus.selectedId(), renderedPanX, renderedPanY);
            explicitFocusPending = false;
        }
        EditorSelectionFocus.Point point = selectionFocus.advance(gestureActive(), renderedPanX, renderedPanY,
                () -> new EditorSelectionFocus.Point(
                        QuestViewportMath.panForGraphPoint(focus.targetGraphX(), focus.targetScreenX(),
                                focus.screenOriginX(), renderedZoom),
                        QuestViewportMath.panForGraphPoint(focus.targetGraphY(), focus.targetScreenY(),
                                focus.screenOriginY(), renderedZoom)),
                seconds, focusSpeed);
        panX = renderedPanX = point.x();
        panY = renderedPanY = point.y();
        if (!explicit || selectionFocus.focusing() == null) explicitFocusId = null;
    }

    ClickResult mouseClicked(QuestCanvasRenderer.Frame frame, InputModel model,
                             double x, double y, int button, boolean controlDown, long nowNanos) {
        if (frame == null || !frame.matches(model.identity())
                || !Objects.equals(frame.chapterId(), model.chapterId())
                || !frame.viewport().containsExclusive(x, y)) {
            return new ClickResult(false, null);
        }
        adoptRenderedCamera();
        ResourceLocation questHit = QuestCanvasRenderer.nodeAt(frame, model.identity(), x, y);
        CanvasScene.Decoration artwork = questHit == null && model.editing()
                ? CanvasArtwork.hit(model.decorations(), frame.camera(), x, y) : null;
        ResourceLocation hit = questHit != null ? CanvasSelectionKey.quest(questHit)
                : artwork == null ? null : CanvasSelectionKey.decoration(artwork.id());
        if (hit != null) {
            DraftBookEditor.Position hitPosition = questHit != null ? model.chapterPositions().get(questHit)
                    : new DraftBookEditor.Position(artwork.x(), artwork.y());
            if (model.editing()) {
                if (button == 1) {
                    if (!selection.contains(hit)) { selection.clear(); selection.add(hit); }
                    return new ClickResult(true, Intent.context(questHit != null ? Action.OPEN_NODE_CONTEXT : Action.OPEN_DECORATION_CONTEXT,
                            CanvasSelectionKey.id(hit), x, y, hitPosition == null ? 0 : hitPosition.x(), hitPosition == null ? 0 : hitPosition.y()));
                }
                if (button == 0) {
                    nodeDrag.rememberSelection(selection);
                    // Ctrl only changes membership. In particular, deselection must not start a drag of the remaining group.
                    selectionOnlyClick = controlDown || selection.size() > 1 || artwork != null;
                    if (controlDown) {
                        if (!selection.add(hit)) selection.remove(hit);
                    } else if (!selection.contains(hit)) {
                        selection.clear(); selection.add(hit);
                    }
                    if (!selection.contains(hit) || artwork != null && artwork.locked()) return new ClickResult(true, null);
                    Map<ResourceLocation, DraftBookEditor.Position> positions = new LinkedHashMap<>();
                    model.chapterPositions().forEach((id, value) -> positions.put(CanvasSelectionKey.quest(id), value));
                    model.decorations().stream().filter(d -> !d.locked()).forEach(d -> positions.put(
                            CanvasSelectionKey.decoration(d.id()), new DraftBookEditor.Position(d.x(), d.y())));
                    beginNodeDrag(hit, frame.camera().graphX(x), frame.camera().graphY(y), x, y,
                            positions, model.identity(), nowNanos, model.snapToGrid());
                    return new ClickResult(true, null);
                }
            } else if (button == 0) {
                return new ClickResult(true, Intent.details(questHit));
            }
        }
        if (model.editing() && button == 1) {
            return new ClickResult(true, Intent.context(Action.OPEN_CANVAS_CONTEXT, null, x, y,
                    frame.camera().graphX(x) / QuestViewportMath.GRID_SCALE,
                    frame.camera().graphY(y) / QuestViewportMath.GRID_SCALE));
        }
        if (button != 0) return new ClickResult(true, null);
        if (model.editing() && !controlDown) selection.clear();
        panning = true;
        gestureIdentity = model.identity();
        dragX = x;
        dragY = y;
        return new ClickResult(true, null);
    }

    GestureResult mouseDragged(QuestCanvasRenderer.Frame frame, QuestScreenFrameIdentity current,
                               double x, double y, int button, long nowNanos) {
        if (button != 0 || !sameGestureFrame(frame, current)) return new GestureResult(false, null);
        if (nodeDrag.active()) {
            if (!nodeDrag.pickedUp() && nodeDrag.requestsPan(x, y, nowNanos)) {
                // Travel before the hold threshold becomes a pan and restores pre-press selection.
                double pressX = nodeDrag.pressX(), pressY = nodeDrag.pressY();
                Set<ResourceLocation> beforePress = nodeDrag.previousSelection();
                nodeDrag.cancel();
                selection.clear();
                selection.addAll(beforePress);
                panning = true;
                panX += x - pressX;
                panY += y - pressY;
                renderedPanX = panX;
                renderedPanY = panY;
                dragX = x;
                dragY = y;
                return new GestureResult(true, null);
            }
            nodeDrag.update(frame.camera().graphX(x), frame.camera().graphY(y), x, y, nowNanos);
            return new GestureResult(true, null);
        }
        if (panning) {
            panX += x - dragX;
            panY += y - dragY;
            renderedPanX = panX;
            renderedPanY = panY;
            dragX = x;
            dragY = y;
            return new GestureResult(true, null);
        }
        return new GestureResult(false, null);
    }

    GestureResult mouseReleased(QuestCanvasRenderer.Frame frame, QuestScreenFrameIdentity current,
                                int button) {
        if (button != 0 || (!nodeDrag.active() && !panning)) return new GestureResult(false, null);
        if (!sameGestureFrame(frame, current)) {
            cancelGesture();
            return new GestureResult(true, null);
        }
        if (nodeDrag.active()) {
            if (nodeDrag.pickedUp()) {
                Map<ResourceLocation, DraftBookEditor.Position> positions = nodeDrag.releaseMove();
                gestureIdentity = null;
                return new GestureResult(true, positions.isEmpty() ? null : Intent.move(positions));
            }
            ResourceLocation clicked = nodeDrag.anchor();
            nodeDrag.cancel();
            gestureIdentity = null;
            return new GestureResult(true, selectionOnlyClick ? null : Intent.details(CanvasSelectionKey.id(clicked)));
        }
        panning = false;
        gestureIdentity = null;
        return new GestureResult(true, null);
    }

    void scrollZoom(double amount) {
        cancelFocus();
        zoomMotion.target(QuestViewportMath.clampZoom(zoomMotion.target() + amount * BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.zoomStep)));
    }

    void reconcile(java.util.function.Function<ResourceLocation, DraftBookEditor.Position> authoritative,
                   boolean rejected) {
        reconcile(authoritative, id -> null, rejected);
    }

    void reconcile(java.util.function.Function<ResourceLocation, DraftBookEditor.Position> quests,
                   java.util.function.Function<ResourceLocation, DraftBookEditor.Position> decorations, boolean rejected) {
        java.util.function.Function<ResourceLocation, DraftBookEditor.Position> authoritative = key ->
                (CanvasSelectionKey.isDecoration(key) ? decorations : quests).apply(CanvasSelectionKey.id(key));
        nodeDrag.reconcile(authoritative, rejected);
        // Drop vanished objects only after an authoritative snapshot; typed IDs cannot collide.
        selection.removeIf(id -> authoritative.apply(id) == null);
    }

    void cancelGesture() {
        nodeDrag.cancel();
        panning = false;
        gestureIdentity = null;
    }

    void cancelFocus() {
        explicitFocusId = null;
        explicitFocusPending = false;
        selectionFocus.cancel(renderedPanX, renderedPanY);
        panX = renderedPanX;
        panY = renderedPanY;
    }

    void finishZoomMotion(double screenOriginX, double screenOriginY) {
        double targetZoom = zoomMotion.target();
        if (Double.compare(zoom, targetZoom) != 0) {
            panX = QuestViewportMath.panForStableAnchor(screenOriginX, screenOriginX, panX, zoom, targetZoom);
            panY = QuestViewportMath.panForStableAnchor(screenOriginY, screenOriginY, panY, zoom, targetZoom);
            zoom = targetZoom;
        }
        zoomMotion.snap(zoom);
        renderedPanX = panX;
        renderedPanY = panY;
        renderedZoom = zoom;
    }

    private void adoptRenderedCamera() {
        cancelFocus();
        zoom = renderedZoom;
        zoomMotion.snap(zoom);
        panX = renderedPanX;
        panY = renderedPanY;
    }

    void selectOnly(ResourceLocation id) {
        selection.clear();
        selection.add(CanvasSelectionKey.quest(id));
    }

    private void beginNodeDrag(ResourceLocation anchor, double graphX, double graphY, double screenX, double screenY,
                               Map<ResourceLocation, DraftBookEditor.Position> positions,
                               QuestScreenFrameIdentity identity, long nowNanos, boolean snapToGrid) {
        Map<ResourceLocation, DraftBookEditor.Position> origins = new LinkedHashMap<>();
        positions.forEach((id, position) -> {
            if (selection.contains(id)) origins.put(id, position);
        });
        nodeDrag.begin(origins, anchor, graphX, graphY, screenX, screenY, nowNanos, snapToGrid);
        if (nodeDrag.active()) {
            panning = false;
            gestureIdentity = identity;
        }
    }

    private boolean sameGestureFrame(QuestCanvasRenderer.Frame frame, QuestScreenFrameIdentity current) {
        return gestureIdentity != null && gestureIdentity.equals(current)
                && frame != null && frame.matches(current);
    }
}

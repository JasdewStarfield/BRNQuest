package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor.Position;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure gesture state. It returns a move intent once; the screen still validates and submits it. */
final class QuestNodeDrag {
    private static final long HOLD_NANOS = 220_000_000L;
    private final Map<ResourceLocation, Position> preview = new LinkedHashMap<>();
    private final Map<ResourceLocation, Position> snapped = new LinkedHashMap<>();
    private Map<ResourceLocation, Position> origins = Map.of();
    private boolean active;
    private boolean pickedUp;
    private boolean moved;
    private long started;
    private ResourceLocation anchor;
    private double startX, startY, pressX, pressY;
    private Set<ResourceLocation> selectionBeforePress = Set.of();

    boolean active() { return active; }
    boolean pickedUp() { return pickedUp; }
    ResourceLocation anchor() { return anchor; }
    double pressX() { return pressX; }
    double pressY() { return pressY; }
    Set<ResourceLocation> previousSelection() { return selectionBeforePress; }
    void rememberSelection(Set<ResourceLocation> selection) { selectionBeforePress = Set.copyOf(selection); }
    Position preview(ResourceLocation id) { return preview.get(id); }
    Position snapPreview(ResourceLocation id) { return snapped.get(id); }
    void clearPreview() { preview.clear(); }

    void begin(Map<ResourceLocation, Position> positions, ResourceLocation anchor,
               double graphX, double graphY, double screenX, double screenY, long now) {
        if (positions.isEmpty()) return;
        origins = Map.copyOf(positions);
        preview.clear();
        preview.putAll(origins);
        snapped.clear();
        this.anchor = anchor;
        startX = graphX;
        startY = graphY;
        pressX = screenX;
        pressY = screenY;
        started = now;
        pickedUp = false;
        moved = false;
        active = true;
    }

    boolean requestsPan(double x, double y, long now) {
        return QuestViewportMath.shouldPanBeforeLongPress(now - started, HOLD_NANOS,
                x - pressX, y - pressY, 4.0);
    }

    void update(double graphX, double graphY, double screenX, double screenY, long now) {
        if (!active || origins.isEmpty()) return;
        if (!pickedUp && requestsPan(screenX, screenY, now)) return;
        if (!pickedUp && now - started >= HOLD_NANOS) pickedUp = true;
        if (!pickedUp) return;
        double dx = (graphX - startX) / QuestViewportMath.GRID_SCALE;
        double dy = (graphY - startY) / QuestViewportMath.GRID_SCALE;
        moved = Math.hypot(graphX - startX, graphY - startY) >= 2.0;
        preview.clear();
        origins.forEach((id, point) -> preview.put(id, new Position(point.x() + dx, point.y() + dy)));
        snapped.clear();
        Position point = origins.get(anchor);
        if (!moved || point == null) return;
        double sx = QuestViewportMath.snappedGroupDelta(point.x(), dx);
        double sy = QuestViewportMath.snappedGroupDelta(point.y(), dy);
        origins.forEach((id, origin) -> snapped.put(id, new Position(
                QuestViewportMath.limitDraggedPrecision(origin.x() + sx),
                QuestViewportMath.limitDraggedPrecision(origin.y() + sy))));
    }

    /** Empty/no-op releases never create a revision; repeated release cannot duplicate a mutation. */
    Map<ResourceLocation, Position> releaseMove() {
        if (!active) return Map.of();
        if (!pickedUp || !moved || snapped.isEmpty() || snapped.equals(origins)) {
            cancel();
            return Map.of();
        }
        Map<ResourceLocation, Position> result = Map.copyOf(snapped);
        cancel();
        // Keep the server-bound position until reconciliation confirms or rejects it.
        preview.putAll(result);
        return result;
    }

    void cancel() {
        active = false;
        pickedUp = false;
        moved = false;
        anchor = null;
        started = 0;
        pressX = 0;
        pressY = 0;
        selectionBeforePress = Set.of();
        origins = Map.of();
        preview.clear();
        snapped.clear();
    }

    void reconcile(java.util.function.Function<ResourceLocation, Position> authoritative, boolean rejected) {
        if (active || preview.isEmpty()) return;
        if (rejected || preview.entrySet().stream().allMatch(e -> e.getValue().equals(authoritative.apply(e.getKey())))) {
            preview.clear();
        }
    }
}

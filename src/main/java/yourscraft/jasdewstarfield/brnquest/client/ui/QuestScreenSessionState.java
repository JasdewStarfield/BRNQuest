package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Process-local quest-screen memory. Nothing is serialized, so closing the game
 * naturally resets the camera and navigation preferences.
 */
final class QuestScreenSessionState {
    private static final Map<Key, Snapshot> snapshots = new HashMap<>();

    private static final Map<String, Boolean> editorPreferences = new HashMap<>();

    /** Remember intent only; reopening must acquire a fresh server-authorized session. */
    static synchronized void rememberEditing(String serverId, boolean editing) {
        editorPreferences.put(normalizeServerId(serverId), editing);
    }

    static synchronized boolean reopenEditing(String serverId) {
        if (!BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.rememberEditingMode)) return false;
        return editorPreferences.getOrDefault(normalizeServerId(serverId), false);
    }

    private QuestScreenSessionState() {}

    static synchronized Snapshot load(String serverId, ResourceLocation bookId) {
        if (bookId == null || !BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.rememberViewport)) return Snapshot.defaults();
        return snapshots.getOrDefault(new Key(normalizeServerId(serverId), bookId), Snapshot.defaults());
    }

    static synchronized void save(String serverId, ResourceLocation bookId, ResourceLocation chapterId,
                                  double centerX, double centerY,
                                  double zoom, boolean navigationCollapsed) {
        save(serverId, bookId, chapterId, centerX, centerY, zoom, navigationCollapsed, Set.of());
    }

    /** Group folding has the same server/book scope and lifetime as the remembered camera. */
    static synchronized void save(String serverId, ResourceLocation bookId, ResourceLocation chapterId,
                                  double centerX, double centerY, double zoom, boolean navigationCollapsed,
                                  Set<ResourceLocation> foldedGroups) {
        if (bookId == null) return;
        snapshots.put(new Key(normalizeServerId(serverId), bookId), new Snapshot(chapterId, centerX, centerY,
                QuestViewportMath.clampZoom(zoom), navigationCollapsed, foldedGroups));
    }

    static synchronized void clearForTest() { snapshots.clear(); editorPreferences.clear(); }

    private static String normalizeServerId(String serverId) {
        return serverId == null || serverId.isBlank() ? "unknown" : serverId;
    }

    private record Key(String serverId, ResourceLocation bookId) {}

    /** Immutable value prevents one screen instance from mutating another's cached state. */
    record Snapshot(ResourceLocation chapterId, double centerX, double centerY,
                    double zoom, boolean navigationCollapsed, Set<ResourceLocation> foldedGroups) {
        Snapshot { foldedGroups = Set.copyOf(foldedGroups); }

        static Snapshot defaults() {
            return new Snapshot(null, 0.0, 0.0, 1.0, true, Set.of());
        }
    }
}

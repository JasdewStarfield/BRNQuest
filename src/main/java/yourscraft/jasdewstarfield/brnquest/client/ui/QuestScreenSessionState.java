package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;

/**
 * Process-local quest-screen memory. Nothing is serialized, so closing the game
 * naturally resets the camera and navigation preferences.
 */
final class QuestScreenSessionState {
    private static Snapshot current = Snapshot.defaults();

    private QuestScreenSessionState() {}

    static synchronized Snapshot load() {
        return current;
    }

    static synchronized void save(ResourceLocation chapterId, double centerX, double centerY,
                                  double zoom, boolean navigationCollapsed) {
        current = new Snapshot(chapterId, centerX, centerY,
                QuestViewportMath.clampZoom(zoom), navigationCollapsed);
    }

    /** Immutable value prevents one screen instance from mutating another's cached state. */
    record Snapshot(ResourceLocation chapterId, double centerX, double centerY,
                    double zoom, boolean navigationCollapsed) {
        static Snapshot defaults() {
            return new Snapshot(null, 0.0, 0.0, 1.0, true);
        }
    }
}

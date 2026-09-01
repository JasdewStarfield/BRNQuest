package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import yourscraft.jasdewstarfield.brnquest.data.QuestAppearance;

/** Shared node measurements used by both canvas rendering and pointer hit testing. */
public final class QuestNodeGeometry {
    private QuestNodeGeometry() {}

    public static int visualSize(int baseSize, QuestAppearance appearance) {
        double authored = baseSize * positiveScale(appearance.size());
        double minimum = baseSize * Math.max(0.0,
                Double.isFinite(appearance.minWidth()) ? appearance.minWidth() : 0.0);
        return Math.max(6, (int) Math.round(Math.max(authored, minimum)));
    }

    /** The two-pixel allowance matches the visible outline without retaining a fixed legacy radius. */
    public static int hitRadius(int baseSize, QuestAppearance appearance) {
        return visualSize(baseSize, appearance) / 2 + 2;
    }

    private static double positiveScale(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Pure text fitting helpers shared by compact editor rows. */
public final class EditorTextLayout {
    private EditorTextLayout() {}

    public static float fittedScale(int measuredWidth, int availableWidth, float minimumScale) {
        if (measuredWidth <= 0 || availableWidth >= measuredWidth) return 1.0F;
        float safeMinimum = Math.max(0.1F, Math.min(1.0F, minimumScale));
        return Math.max(safeMinimum, availableWidth / (float) measuredWidth);
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** One geometry model drives rendering, widgets and divider input for all three manual views. */
final class TextEditorLayout {
    enum View { SOURCE, PREVIEW, SPLIT }
    static final int MINIMUM_PANE = 120;
    final UiRect panel, body, source, preview, divider;
    final boolean split;
    final int controlsTop, metadataTop, toolbarTop;

    TextEditorLayout(int width, int height, View preferred, double ratio, int metadataRows, int controlsWidth) {
        int margin = Math.min(12, Math.max(0, Math.min(width, height) / 24));
        int w = Math.min(1000, Math.max(1, width - 2 * margin));
        int h = Math.min(680, Math.max(1, height - 2 * margin));
        int left = (width - w) / 2, top = (height - h) / 2;
        panel = new UiRect(left, top, left + w, top + h);
        controlsTop = top + 24;
        metadataTop = controlsTop + (w - 24 >= controlsWidth ? 24 : 48);
        toolbarTop = metadataTop + metadataRows * 24;
        int bodyTop = Math.min(panel.bottom() - 48, toolbarTop + 25);
        body = new UiRect(left + 10, Math.max(top, bodyTop), left + w - 10, Math.max(top, panel.bottom() - 48));
        split = preferred == View.SPLIT && body.width() >= 2 * MINIMUM_PANE + 8;
        int available = Math.max(0, body.width() - 8);
        int sourceWidth = split ? Math.clamp((int) Math.round(available * ratio), MINIMUM_PANE, available - MINIMUM_PANE) : body.width();
        source = split ? new UiRect(body.left(), body.top(), body.left() + sourceWidth, body.bottom()) : body;
        divider = split ? new UiRect(source.right(), body.top(), source.right() + 8, body.bottom())
                : new UiRect(body.left(), body.top(), body.left(), body.top());
        preview = split ? new UiRect(divider.right(), body.top(), body.right(), body.bottom()) : body;
    }
    static double dragRatio(UiRect body, double x) {
        return Math.clamp((x - body.left()) / Math.max(1, body.width() - 8), 0.0, 1.0);
    }
}

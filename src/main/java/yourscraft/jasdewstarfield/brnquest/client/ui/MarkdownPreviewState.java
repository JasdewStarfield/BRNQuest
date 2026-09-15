package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;
import yourscraft.jasdewstarfield.brnquest.data.text.ResolvedDocument;

/** Coalesces rapid editor changes so CommonMark parsing never runs once per render frame. */
final class MarkdownPreviewState {
    static final long DEBOUNCE_MILLIS = 150L;
    private ResolvedDocument visible;
    private ResolvedDocument pending;
    private long dueAt = Long.MAX_VALUE;

    void showNow(String text, DocumentFormat format, String locale) {
        visible = new ResolvedDocument(text, format, locale);
        pending = null;
        dueAt = Long.MAX_VALUE;
    }

    void schedule(String text, DocumentFormat format, String locale, long now) {
        pending = new ResolvedDocument(text, format, locale);
        dueAt = now + DEBOUNCE_MILLIS;
    }

    boolean advance(long now) {
        if (pending == null || now < dueAt) return false;
        visible = pending;
        pending = null;
        dueAt = Long.MAX_VALUE;
        return true;
    }

    ResolvedDocument visible() {
        return visible == null ? new ResolvedDocument("", DocumentFormat.PLAIN, "en_us") : visible;
    }
}

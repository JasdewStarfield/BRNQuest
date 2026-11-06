package yourscraft.jasdewstarfield.brnquest.client;

import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import java.nio.charset.StandardCharsets;

/** Local immutable predictions and bounded history; authoritative drafts stay with the session. */
final class EditorPreviewHistory {
    // The pending projection is never exposed by draft(), used for a packet revision, or installed into ClientQuestState.
    private QuestBookSnapshot displayPreview;
    private QuestBookSnapshot mutationBase;
    private int historyDirection;
    private record PreviewCheckpoint(QuestBookSnapshot snapshot, int bytes) {
        static PreviewCheckpoint of(QuestBookSnapshot snapshot) {
            return new PreviewCheckpoint(snapshot, NativeBookJson.encode(snapshot.book()).getBytes(StandardCharsets.UTF_8).length);
        }
    }
    private final java.util.Deque<PreviewCheckpoint> undoPreviews = new java.util.ArrayDeque<>();
    private final java.util.Deque<PreviewCheckpoint> redoPreviews = new java.util.ArrayDeque<>();
    private static final int MAX_PREVIEW_HISTORY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PREVIEW_HISTORY_STEPS = 16;
    void beginMutation(QuestBookSnapshot draft) {
        mutationBase = draft;
        displayPreview = null;
        historyDirection = 0;
    }

    void beginHistory(QuestBookSnapshot draft, boolean redo) {
        mutationBase = draft;
        historyDirection = redo ? 1 : -1;
        var checkpoint = (redo ? redoPreviews : undoPreviews).peekLast();
        displayPreview = checkpoint == null ? null : checkpoint.snapshot();
    }

    void preview(java.util.function.UnaryOperator<QuestBookDefinition> edit) {
        if (mutationBase == null || displayPreview != null || historyDirection != 0) return;
        try {
            var value = edit.apply(mutationBase.book());
            if (!value.id().equals(mutationBase.book().id())) return;
            var candidate = QuestBookSnapshot.of(value);
            if (candidate.quests().size() <= BrnQuestConstants.MAX_QUESTS) displayPreview = candidate;
        } catch (RuntimeException ignored) {
            // Unsupported projections still go through the server's normal validation path.
        }
    }

    QuestBookSnapshot display(QuestBookSnapshot draft) { return displayPreview == null ? draft : displayPreview; }
    boolean pending() { return displayPreview != null; }
    void clear() { discardPreview(); undoPreviews.clear(); redoPreviews.clear(); }

    /** Confirm only after full content/revision verification. Never use a projection as a patch base. */
    QuestBookSnapshot confirm(QuestBookSnapshot draft, QuestBookSnapshot candidate) {
        if (mutationBase == null && draft != null && !draft.revision().equals(candidate.revision())) {
            // An authoritative replacement outside our tracked request invalidates cached history predictions.
            undoPreviews.clear(); redoPreviews.clear();
        }
        if (mutationBase != null && !mutationBase.revision().equals(candidate.revision())) {
            if (historyDirection == 0) {
                undoPreviews.addLast(PreviewCheckpoint.of(mutationBase));
                redoPreviews.clear();
            } else {
                var from = historyDirection > 0 ? redoPreviews : undoPreviews;
                var to = historyDirection > 0 ? undoPreviews : redoPreviews;
                if (!from.isEmpty() && from.peekLast().snapshot().revision().equals(candidate.revision())) {
                    from.removeLast(); to.addLast(PreviewCheckpoint.of(mutationBase));
                } else { undoPreviews.clear(); redoPreviews.clear(); }
            }
            trimPreviewHistory();
        }
        // Reuse the already-visible immutable snapshot when the server verifies exactly the same content.
        var confirmed = displayPreview != null && displayPreview.revision().equals(candidate.revision()) ? displayPreview : candidate;
        discardPreview();
        return confirmed;
    }
    void discardPreview() { displayPreview = null; mutationBase = null; historyDirection = 0; }
    private void trimPreviewHistory() {
        // Whole snapshots avoid replaying server normalization; bound both memory and retained history depth.
        long bytes = java.util.stream.Stream.concat(undoPreviews.stream(), redoPreviews.stream())
                .mapToLong(PreviewCheckpoint::bytes).sum();
        while (undoPreviews.size()+redoPreviews.size() > MAX_PREVIEW_HISTORY_STEPS || bytes > MAX_PREVIEW_HISTORY_BYTES) {
            var queue = undoPreviews.isEmpty() ? redoPreviews : undoPreviews;
            var removed = queue.removeFirst();
            bytes -= removed.bytes();
        }
    }

}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import java.util.*;

/** Arrivals display immediately; every card in the stack shares the latest arrival's closing clock. */
public final class QuestNotificationQueue {
    public enum Kind { TASK, QUEST, REWARD }
    public record Notice(String eventId, String questId, String targetId, Kind kind) {}
    public record Active(List<Notice> notices, long revealedAt, long elapsedNanos, String id) {
        public Active { notices = List.copyOf(notices); }
        /** Preserve callers that create standalone cards without a separate animation identity. */
        public Active(List<Notice> notices, long revealedAt, long elapsedNanos) {
            this(notices, revealedAt, elapsedNanos, notices.getFirst().eventId());
        }
    }
    public record Stack(List<Active> visible, int foldedCount, boolean exitAllowed) {
        public Stack { visible = List.copyOf(visible); }
        public boolean isEmpty() { return visible.isEmpty(); }
    }
    private static final long MERGE_NANOS = 500_000_000L;
    private static final int MAX_CARDS = 3, MAX_BATCH = 32, MAX_SEEN = 512;
    private static final class Batch {
        final List<Notice> notices = new ArrayList<>();
        final String id;
        long receivedAt;
        Batch(Notice notice, long now) { id = notice.eventId(); notices.add(notice); receivedAt = now; }
        Active view(long startedAt, long elapsed) { return new Active(notices, startedAt, elapsed, id); }
    }
    private final ArrayDeque<Batch> layers = new ArrayDeque<>();
    private final LinkedHashSet<String> seen = new LinkedHashSet<>();
    private long lastArrival;
    private long lastDuration = Long.MAX_VALUE;
    private int discardedCount;

    public void add(Notice notice, long now) {
        add(notice, now, lastDuration);
    }

    /** The overlay supplies the current duration so a new arrival cannot revive an expired stack. */
    public void add(Notice notice, long now, long durationNanos) {
        if (!seen.add(notice.eventId())) return;
        if (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
        expire(now, durationNanos);
        // A late objective packet must not replace its own already displayed whole-quest completion.
        if (notice.kind() == Kind.TASK && layers.stream().anyMatch(batch -> nearby(batch, notice, now)
                && batch.notices.getFirst().kind() == Kind.QUEST)) return;
        Batch replacement = null;
        if (notice.kind() == Kind.QUEST) {
            // Reuse the newest matching card's animation identity while changing its content in place.
            // Real event IDs remain in notices and deduplication; only the card ID stays stable.
            for (var iterator = layers.iterator(); iterator.hasNext();) {
                var batch = iterator.next();
                if (nearby(batch, notice, now) && batch.notices.getFirst().kind() == Kind.TASK) {
                    if (replacement == null) replacement = batch;
                    iterator.remove();
                }
            }
        }
        var front = layers.peekFirst();
        if (replacement != null) {
            replacement.notices.clear(); replacement.notices.add(notice); replacement.receivedAt = now;
            layers.addFirst(replacement);
        } else if (front != null && nearby(front, notice, now) && front.notices.size() < MAX_BATCH
                && front.notices.getFirst().kind() == notice.kind()) front.notices.add(notice);
        else layers.addFirst(new Batch(notice, now));
        // Merging is an in-place update, never a half-second wait before showing the first event.
        lastArrival = now;
        if (layers.size() > MAX_CARDS) {
            layers.removeLast();
            // Older content is discarded permanently; only a saturating count survives.
            if (discardedCount < Integer.MAX_VALUE - (MAX_CARDS - 1)) discardedCount++;
        }
    }

    private static boolean nearby(Batch batch, Notice notice, long now) {
        return now >= batch.receivedAt && now - batch.receivedAt <= MERGE_NANOS
                && batch.notices.getFirst().questId().equals(notice.questId());
    }

    private void expire(long now, long durationNanos) {
        lastDuration = durationNanos;
        if (!layers.isEmpty() && Math.max(0, now - lastArrival) >= durationNanos) {
            // Retire the whole stack together; folded cards never become another timed foreground notice.
            layers.clear();
            discardedCount = 0;
        }
    }

    /** Duration is supplied each frame so client setting changes also affect an existing stack. */
    public Stack stack(long now, long durationNanos) {
        expire(now, durationNanos);
        long elapsed = Math.max(0, now - lastArrival);
        return new Stack(layers.stream().limit(3).map(batch -> batch.view(lastArrival, elapsed)).toList(),
                discardedCount + Math.max(0, layers.size() - 1), !layers.isEmpty());
    }

    /** World, book and owner changes discard transient display and deduplication history. */
    public void clear() { layers.clear(); seen.clear(); discardedCount = 0; lastArrival = 0; lastDuration = Long.MAX_VALUE; }
}

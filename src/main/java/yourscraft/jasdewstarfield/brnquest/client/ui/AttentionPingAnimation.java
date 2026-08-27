package yourscraft.jasdewstarfield.brnquest.client.ui;

/**
 * Produces the shared vertical offset for actionable task and reward badges.
 * Most of each cycle is deliberately still, followed by three short hops so the
 * notification remains noticeable without continuously pulsing over quest content.
 */
final class AttentionPingAnimation {
    static final long CYCLE_NANOS = 3_000_000_000L;
    static final long INITIAL_IDLE_NANOS = 1_650_000_000L;
    static final long HOP_STRIDE_NANOS = 360_000_000L;
    static final long HOP_ACTIVE_NANOS = 240_000_000L;
    static final int HOP_COUNT = 3;
    private static final int HOP_HEIGHT = 2;

    private AttentionPingAnimation() {}

    static int verticalOffset(long frameNanos) {
        long phase = Math.floorMod(frameNanos, CYCLE_NANOS);
        if (phase < INITIAL_IDLE_NANOS) return 0;

        long hoppingPhase = phase - INITIAL_IDLE_NANOS;
        int hopIndex = (int) (hoppingPhase / HOP_STRIDE_NANOS);
        if (hopIndex >= HOP_COUNT) return 0;
        long withinHop = hoppingPhase % HOP_STRIDE_NANOS;
        if (withinHop >= HOP_ACTIVE_NANOS) return 0;

        double progress = withinHop / (double) HOP_ACTIVE_NANOS;
        return -(int) Math.round(Math.sin(progress * Math.PI) * HOP_HEIGHT);
    }
}

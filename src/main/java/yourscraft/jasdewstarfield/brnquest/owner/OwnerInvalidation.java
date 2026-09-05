package yourscraft.jasdewstarfield.brnquest.owner;

import java.util.concurrent.atomic.AtomicLong;

/** Optional hooks only advance a signal; the server re-queries authoritative APIs next tick. */
public final class OwnerInvalidation {
    private static final AtomicLong GENERATION = new AtomicLong();
    private OwnerInvalidation() {}
    public static void invalidate() { GENERATION.incrementAndGet(); }
    public static long generation() { return GENERATION.get(); }
}

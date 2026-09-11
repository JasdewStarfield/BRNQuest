package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** One-shot suspension token: child/JEI transitions do not become a real editor-session exit. */
public final class TransientScreenLifecycle {
    private boolean openingChild;
    public void prepareChild() { openingChild = true; }
    /** Arm suspension before setScreen invokes the parent's removed callback, and clear failed transitions. */
    public void openChild(Runnable transition) {
        prepareChild();
        try {
            transition.run();
        } catch (RuntimeException | Error failure) {
            openingChild = false;
            throw failure;
        }
    }
    public boolean consumeRemoval() {
        boolean suspended = openingChild;
        openingChild = false;
        return suspended;
    }
    /** A failed/re-entrant transition must not suppress a later genuine screen removal. */
    public void returnedToParent() { openingChild = false; }
}

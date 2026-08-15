package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/**
 * Tracks one input-capturing editor overlay. Payload and semantic actions stay
 * in QuestScreen, while mutual exclusion is explicit and reusable.
 */
public final class EditorOverlayHost {
    public enum Kind { NONE, CATALOG, CONTEXT_MENU, STRUCTURE_FORM, DELETE_CONFIRMATION, DISCARD_CONFIRMATION }

    private Kind active = Kind.NONE;

    public Kind active() {
        return active;
    }

    public boolean isOpen(Kind kind) {
        return active == kind;
    }

    public void show(Kind kind) {
        if (kind == Kind.NONE) throw new IllegalArgumentException("Use close() to clear the active overlay");
        active = kind;
    }

    public void close() {
        active = Kind.NONE;
    }
}

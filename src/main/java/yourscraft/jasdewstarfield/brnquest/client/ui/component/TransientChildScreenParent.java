package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Marks a screen whose editor/session state must survive a temporary external child screen. */
public interface TransientChildScreenParent {
    void prepareForTransientChildScreen();
}

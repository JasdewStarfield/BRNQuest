package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Explicit opt-in keeps native chat/config inputs unchanged by the authoring-only mixins. */
public interface RawTextInput {
    boolean brnquest$rawText();
    default void brnquest$rawText(boolean enabled) {}
}

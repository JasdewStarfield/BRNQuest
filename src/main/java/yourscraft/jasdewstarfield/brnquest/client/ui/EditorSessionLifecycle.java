package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;

/** Emits one invalidation per lease/book transition, shared by every editing module. */
final class EditorSessionLifecycle {
    record Scope(UUID session, ResourceLocation book, boolean editing) {}
    private Scope previous;
    /** Only an authoritative denial revokes input; heartbeat latency and retryable form errors retain it. */
    static boolean canEdit(ClientEditorState editor) {
        return editor.editing() && editor.allowed()
                && !(editor.mode() == ClientEditorState.Mode.ERROR
                && ("SESSION_EXPIRED".equals(editor.statusCode()) || "EDITOR_PERMISSION_REQUIRED".equals(editor.statusCode())));
    }
    void accept(Scope current, Runnable changed) {
        var old = previous;
        previous = current;
        if (old != null && !old.equals(current)) changed.run();
    }
}

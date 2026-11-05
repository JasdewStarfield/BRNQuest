package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Emits one invalidation per lease/book transition, shared by every editing module. */
final class EditorSessionLifecycle {
    record Scope(UUID session, ResourceLocation book, boolean editing) {}
    private Scope previous;
    void accept(Scope current, Runnable changed) {
        var old = previous;
        previous = current;
        if (old != null && !old.equals(current)) changed.run();
    }
}

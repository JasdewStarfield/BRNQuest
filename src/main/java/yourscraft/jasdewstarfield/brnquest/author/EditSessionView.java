package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Read-only lease status safe to show to another authorized remote administrator. */
public record EditSessionView(ResourceLocation bookId, UUID editorId, String editorName,
                              String baseRevision, String draftRevision, String savedRevision,
                              long expiresAtTick) {
    public boolean dirty() { return !draftRevision.equals(savedRevision); }
}

package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.util.List;

/** Immutable mutation response shared by commands and the future editor protocol. */
public record DraftEditResult(DraftSnapshot snapshot, List<ResourceLocation> affectedObjects,
                              List<Diagnostic> diagnostics) {
    public DraftEditResult {
        affectedObjects = List.copyOf(affectedObjects);
        diagnostics = List.copyOf(diagnostics);
    }
}

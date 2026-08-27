package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.Optional;

/** Screen-side query used by optional recipe viewers without leaking their API into core UI code. */
public interface RecipeLookupSource {
    Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY);
}

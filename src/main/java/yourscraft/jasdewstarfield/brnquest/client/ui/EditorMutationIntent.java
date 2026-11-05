package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Detached authoring intent; the screen's existing service boundary supplies lease and revision. */
record EditorMutationIntent(String action, ResourceLocation target, ResourceLocation parent, ResourceLocation secondary,
                            String text, int index, double x, double y, List<Position> positions, Map<String, String> config) {
    record Position(ResourceLocation quest, double x, double y) {}
    EditorMutationIntent {
        positions = List.copyOf(positions);
        config = Map.copyOf(config);
    }
    EditorMutationIntent(String action, ResourceLocation target, ResourceLocation parent, ResourceLocation secondary,
                         String text, int index, double x, double y, List<Position> positions) {
        this(action, target, parent, secondary, text, index, x, y, positions, Map.of());
    }
}

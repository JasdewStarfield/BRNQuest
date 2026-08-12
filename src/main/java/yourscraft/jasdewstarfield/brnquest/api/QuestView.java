package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Immutable definition projection that never exposes mutable runtime state. */
public record QuestView(ResourceLocation bookId, ResourceLocation id, ResourceLocation chapterId,
                        String title, List<ResourceLocation> dependencies) {
    public QuestView { dependencies = List.copyOf(dependencies); }
}

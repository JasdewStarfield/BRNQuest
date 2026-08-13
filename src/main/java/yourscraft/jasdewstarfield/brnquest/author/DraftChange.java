package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.util.List;

/** Candidate immutable book plus the stable object IDs touched by one operation. */
public record DraftChange(QuestBookDefinition book, List<ResourceLocation> affectedObjects) {
    public DraftChange {
        affectedObjects = List.copyOf(affectedObjects);
    }
}

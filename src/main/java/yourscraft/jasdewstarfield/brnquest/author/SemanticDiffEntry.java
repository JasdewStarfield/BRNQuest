package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;

/** One deterministic semantic change; raw JSON formatting never appears here. */
public record SemanticDiffEntry(Kind kind, ObjectKind objectKind, ResourceLocation objectId,
                                String path, String before, String after) {
    public enum Kind {
        ADDED, REMOVED, RENAMED, MOVED, ORDER_CHANGED, PROPERTY_CHANGED,
        DEPENDENCY_ADDED, DEPENDENCY_REMOVED, TYPE_CHANGED, CONFIG_CHANGED
    }

    public enum ObjectKind { BOOK, CHAPTER_GROUP, CHAPTER, QUEST, TASK, REWARD }
}

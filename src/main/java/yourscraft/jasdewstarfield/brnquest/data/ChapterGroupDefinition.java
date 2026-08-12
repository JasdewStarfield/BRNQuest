package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;

/** Immutable navigation group owned by one task book. */
public record ChapterGroupDefinition(ResourceLocation bookId, ResourceLocation id, String title, int order) {}

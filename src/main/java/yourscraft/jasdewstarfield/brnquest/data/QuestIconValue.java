package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Backward-compatible encoding for quest-node icons.
 * Existing non-prefixed values remain item-stack SNBT; texture values carry an
 * explicit prefix so a texture ResourceLocation is never mistaken for an item.
 */
public final class QuestIconValue {
    private static final String TEXTURE_PREFIX = "texture:";

    private QuestIconValue() {}

    public static String texture(ResourceLocation textureId) {
        if (textureId == null) throw new IllegalArgumentException("Texture ID is required");
        return TEXTURE_PREFIX + textureId;
    }

    public static boolean isTexture(String value) {
        return value != null && value.startsWith(TEXTURE_PREFIX);
    }

    public static Optional<ResourceLocation> textureId(String value) {
        if (!isTexture(value)) return Optional.empty();
        return Optional.ofNullable(ResourceLocation.tryParse(value.substring(TEXTURE_PREFIX.length())));
    }
}

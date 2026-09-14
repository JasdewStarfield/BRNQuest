package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import java.util.stream.Collectors;

/** Typed canvas keys keep a quest and decoration distinct even when their authored IDs match. */
public final class CanvasSelectionKey {
    private CanvasSelectionKey() {}
    public static ResourceLocation quest(ResourceLocation id) { return key("quest", id); }
    public static ResourceLocation decoration(ResourceLocation id) { return key("decoration", id); }
    private static ResourceLocation key(String kind, ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_selection", kind + "/" + id.getNamespace() + "/" + id.getPath());
    }
    public static boolean isDecoration(ResourceLocation key) { validate(key); return key.getPath().startsWith("decoration/"); }
    public static ResourceLocation id(ResourceLocation key) {
        validate(key);
        String[] parts = key.getPath().split("/", 3);
        return ResourceLocation.fromNamespaceAndPath(parts[1], parts[2]);
    }
    private static void validate(ResourceLocation key) {
        String[] parts = key.getPath().split("/", 3);
        if (!key.getNamespace().equals("brnquest_selection") || parts.length != 3
                || (!parts[0].equals("quest") && !parts[0].equals("decoration")))
            throw new IllegalArgumentException("Invalid typed canvas selection");
    }
    public static Set<ResourceLocation> ids(Set<ResourceLocation> keys, boolean decorations) {
        return keys.stream().filter(k -> isDecoration(k) == decorations).map(CanvasSelectionKey::id).collect(Collectors.toSet());
    }
}

package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.regex.Pattern;

/** Centralizes persistent ID normalization so titles and array positions never become save keys. */
public final class QuestIds {
    private static final Pattern LEGACY = Pattern.compile("[0-9A-Fa-f]{16}");

    private QuestIds() {}

    public static ResourceLocation normalize(String namespace, String value) {
        if (LEGACY.matcher(value).matches()) {
            return ResourceLocation.fromNamespaceAndPath(namespace, "legacy/" + value.toLowerCase(Locale.ROOT));
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) throw new IllegalArgumentException("Invalid resource location: " + value);
        return parsed;
    }

    public static boolean isLegacy(String value) { return LEGACY.matcher(value).matches(); }
}

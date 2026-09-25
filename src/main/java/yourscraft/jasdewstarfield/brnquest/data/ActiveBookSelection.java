package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/** The deployed workspace names the one book that reload should make active. */
public final class ActiveBookSelection {
    public static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(
            "brnquest", "brnquest/active_book.json");

    private ActiveBookSelection() {}

    public static Path file(Path packRoot) {
        return packRoot.resolve("data/brnquest/brnquest/active_book.json");
    }

    public static String encode(ResourceLocation bookId) {
        if (bookId == null) throw new IllegalArgumentException("Active book ID is required");
        return "{\"book\":\"" + bookId + "\"}\n";
    }

    public static ResourceLocation decode(JsonElement value) {
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().has("book")
                || !value.getAsJsonObject().get("book").isJsonPrimitive()) return null;
        return ResourceLocation.tryParse(value.getAsJsonObject().get("book").getAsString());
    }
}

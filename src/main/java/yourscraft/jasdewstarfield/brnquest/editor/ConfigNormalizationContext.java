package yourscraft.jasdewstarfield.brnquest.editor;

import net.minecraft.core.HolderLookup;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Objects;
import java.util.Optional;

/** Read-only registry lookup for type-owned configuration normalization; never exposes a player or world. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ConfigNormalizationContext(Optional<HolderLookup.Provider> registries) {
    public ConfigNormalizationContext {
        Objects.requireNonNull(registries, "registries");
    }

    /** Pure/offline editors cannot resolve dynamic registry values. Types must preserve those values. */
    public static ConfigNormalizationContext withoutRegistries() {
        return new ConfigNormalizationContext(Optional.empty());
    }

    /** Use the current server lookup for authoritative writes; do not retain it across reloads. */
    public static ConfigNormalizationContext withRegistries(HolderLookup.Provider registries) {
        return new ConfigNormalizationContext(Optional.of(registries));
    }
}

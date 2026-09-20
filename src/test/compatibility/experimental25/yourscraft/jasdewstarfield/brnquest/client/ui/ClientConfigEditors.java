package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import java.util.*;
import java.util.function.Consumer;

/** Client-only field editor routing; shared Screens never interpret type-specific configuration trees. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ClientConfigEditors {
    @ApiStatus(ApiStability.EXPERIMENTAL)
    public interface Factory { Screen create(Screen parent, String value, Consumer<String> commit); }
    private record Key(ResourceLocation type, String field) {}
    private static final Map<Key, Factory> EDITORS = new HashMap<>();
    private ClientConfigEditors() {}
    public static void register(ResourceLocation type, String field, Factory factory) {
        if (EDITORS.putIfAbsent(new Key(Objects.requireNonNull(type), Objects.requireNonNull(field)), Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Config editor already registered");
    }
    public static Optional<Factory> find(ResourceLocation type, String field) { return Optional.ofNullable(EDITORS.get(new Key(type, field))); }
}

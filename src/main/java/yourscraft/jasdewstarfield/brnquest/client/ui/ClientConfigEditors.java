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
    public interface Factory {
        Screen create(Screen parent, String value, Consumer<String> commit);
        /** Context is immutable; patches let related fields update together without replacing unknown data. */
        default Screen create(Screen parent, String field, Map<String, String> config, Consumer<Map<String, String>> commit) {
            return create(parent, config.getOrDefault(field, ""), value -> commit.accept(Map.of(field, value)));
        }
        default net.minecraft.network.chat.Component label(String value) {
            return net.minecraft.network.chat.Component.translatable("screen.brnquest.config.edit").append(" · ")
                    .append(yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText.compactValue(value));
        }
        default Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> icon(String value) { return Optional.empty(); }
    }
    /** Type-owned creation flow returns a config map; the author session still owns the mutation. */
    @ApiStatus(ApiStability.EXPERIMENTAL)
    public interface CreationFactory { Screen create(Screen parent, Consumer<Map<String, String>> commit); }
    private record CreationKey(boolean reward, ResourceLocation type) {}
    private static final Map<CreationKey, CreationFactory> CREATION = new HashMap<>();
    public static void registerCreation(boolean reward, ResourceLocation type, CreationFactory factory) {
        if (CREATION.putIfAbsent(new CreationKey(reward, Objects.requireNonNull(type)), Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Creation editor already registered");
    }
    public static Optional<CreationFactory> creation(boolean reward, ResourceLocation type) {
        return Optional.ofNullable(CREATION.get(new CreationKey(reward, type)));
    }
    private record Key(ResourceLocation type, String field) {}
    private static final Map<Key, Factory> EDITORS = new HashMap<>();
    private ClientConfigEditors() {}
    public static void register(ResourceLocation type, String field, Factory factory) {
        if (EDITORS.putIfAbsent(new Key(Objects.requireNonNull(type), Objects.requireNonNull(field)), Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Config editor already registered");
    }
    public static Optional<Factory> find(ResourceLocation type, String field) { return Optional.ofNullable(EDITORS.get(new Key(type, field))); }
}

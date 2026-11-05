package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Builds children against a generic parent; form modules never receive the owning QuestScreen. */
final class EditorChildScreens {
    private final Supplier<Screen> parent;
    private final Consumer<Screen> open;
    private final Consumer<Consumer<ItemStack>> items;
    EditorChildScreens(Supplier<Screen> parent, Consumer<Screen> open, Consumer<Consumer<ItemStack>> items) {
        this.parent = parent; this.open = open; this.items = items;
    }
    void items(Consumer<ItemStack> result) { items.accept(result); }
    void raw(Map<String, String> config, Consumer<Map<String, String>> result) {
        open.accept(new EditorRawConfigScreen(parent.get(), config, result));
    }
    void server(ConfigFieldDescriptor descriptor, String value, Map<String, String> config, Consumer<String> result) {
        Component title = descriptor.labelKey().isBlank() ? Component.literal(descriptor.key()) : Component.translatable(descriptor.labelKey());
        open.accept(new ServerFieldScreen(parent.get(), descriptor.serverSource().orElseThrow().toString(), value, result, config, false, title));
    }
    void custom(ResourceLocation type, String key, Map<String, String> config, Consumer<Map<String, String>> result) {
        open.accept(ClientConfigEditors.find(type, key).orElseThrow().create(parent.get(), key, Map.copyOf(config), result));
    }
    void creation(boolean reward, ResourceLocation type, Consumer<Map<String, String>> result) {
        ClientConfigEditors.creation(reward, type).ifPresent(factory -> open.accept(factory.create(parent.get(), result)));
    }
}

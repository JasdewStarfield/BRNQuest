package yourscraft.jasdewstarfield.brnquest.client.ui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import java.util.List;
import java.util.function.Predicate;
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
    void texture(Consumer<String> result) { open.accept(new EditorTextureBrowserScreen(parent.get(), result)); }
    void locale(List<String> locales, String selected, Consumer<String> result) {
        open.accept(new EditorLocaleScreen(parent.get(), locales, selected, result));
    }
    void choice(Component title, List<EditorChoiceScreen.Choice> choices, String selected, Consumer<String> result) {
        open.accept(new EditorChoiceScreen(parent.get(), title, choices, selected, result));
    }
    void backgrounds(String serverContext, ResourceLocation book, CanvasScene scene, Predicate<CanvasScene> result) {
        open.accept(new EditorCanvasScreen(parent.get(), serverContext, book, false, scene, 1, null, result));
    }
    void defaults(QuestCreationDefaults value, QuestCreationDefaults inherited, Consumer<QuestCreationDefaults> result,
                  boolean hideLines, Consumer<Boolean> hideResult, Boolean consume, boolean inheritedConsume, Consumer<Boolean> consumeResult) {
        open.accept(new EditorCreationDefaultsScreen(parent.get(), value, inherited, result, hideLines, hideResult,
                consume, inheritedConsume, consumeResult));
    }
    void localized(BookLocalization localization, QuestDefinition quest, String locale,
                   Predicate<EditorLocalizedQuestTextScreen.Value> result, EditorLocalizedQuestTextScreen.Value recovery, Runnable cancel) {
        open.accept(new EditorLocalizedQuestTextScreen(parent.get(), localization, quest, locale, result, recovery, cancel));
    }
}

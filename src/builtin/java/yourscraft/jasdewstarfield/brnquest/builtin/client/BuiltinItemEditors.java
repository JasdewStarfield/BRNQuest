package yourscraft.jasdewstarfield.brnquest.builtin.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.builtin.item.ItemChoiceMatcher;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientConfigEditors;
import yourscraft.jasdewstarfield.brnquest.client.ui.EditorItemSelectorScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import java.util.*;
import java.util.function.Consumer;
/** Item config parsing and related-field updates stay outside the generic author screen. */
final class BuiltinItemEditors {
    private BuiltinItemEditors() {}
    static void register() {
        for (var id : List.of(TaskTypes.ITEM, TaskTypes.ITEM_CHOICE)) {
            ClientConfigEditors.register(id, "matcher", new MatcherEditor());
            ClientConfigEditors.registerCreation(false, id, (parent, commit) -> ItemChoiceScreen.createEditor(parent, spec -> {
                var config = new LinkedHashMap<>(patch(spec)); config.put("consume_items", "false");
                commit.accept(Map.copyOf(config));
            }));
        }
        ClientConfigEditors.registerCreation(true, RewardTypes.ITEM, (parent, commit) ->
                new EditorItemSelectorScreen(parent, stack -> {
                    var level = Minecraft.getInstance().level;
                    if (level != null && !stack.isEmpty()) commit.accept(Map.of("item",
                            stack.copyWithCount(1).save(level.registryAccess()).toString(), "count", "1"));
                }));
    }
    private static Map<String, String> patch(ItemChoiceMatcher.Spec spec) {
        return Map.of("matcher", spec.encode(), "required_entries", Integer.toString(spec.requiredEntries()));
    }
    private static final class MatcherEditor implements ClientConfigEditors.Factory {
        public Screen create(Screen parent, String value, Consumer<String> commit) {
            return create(parent, "matcher", Map.of("matcher", value), patch -> commit.accept(patch.get("matcher")));
        }
        public Screen create(Screen parent, String field, Map<String, String> config, Consumer<Map<String, String>> commit) {
            var initial = ItemChoiceMatcher.parse(config.getOrDefault(field, "")).result().orElse(null);
            if (initial != null && config.containsKey("required_entries")) {
                try { initial = initial.withRequiredEntries(Integer.parseInt(config.get("required_entries"))); }
                catch (IllegalArgumentException ignored) { /* The form retains the invalid value and its diagnostic. */ }
            }
            return initial == null ? ItemChoiceScreen.createEditor(parent, spec -> commit.accept(patch(spec)))
                    : new ItemChoiceScreen(parent, initial, true, spec -> commit.accept(patch(spec)));
        }
        private List<net.minecraft.world.item.ItemStack> candidates(String value) {
            var level = Minecraft.getInstance().level;
            var spec = ItemChoiceMatcher.parse(value).result().orElse(null);
            return level == null || spec == null ? List.of() : ItemChoiceMatcher.displayedCandidates(level.registryAccess(), spec);
        }
        public Component label(String value) {
            return Component.translatable("screen.brnquest.editor.typed.property.edit_matcher", candidates(value).size());
        }
        public Optional<EditorIcon> icon(String value) {
            var items = candidates(value);
            return Optional.of(items.isEmpty() ? yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons.named("plus") : EditorIcon.item(items.getFirst()));
        }
    }
}

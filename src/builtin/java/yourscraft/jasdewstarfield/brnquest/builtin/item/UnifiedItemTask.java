package yourscraft.jasdewstarfield.brnquest.builtin.item;
import yourscraft.jasdewstarfield.brnquest.task.*;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;

import java.util.List;
import java.util.Map;

/** Historical item IDs share one canonical matcher and authoritative submission path. */
public final class UnifiedItemTask implements TaskType<Map<String, String>> {
        @Override
        public long craftedProgress(TaskContext context, Map<String, String> config, net.minecraft.world.item.ItemStack crafted) {
            if (!craftedOnly(config)) return context.progress();
            var spec = ItemChoiceMatcher.parseConfig(config).result().orElse(null);
            // One persistent counter can represent only one required crafting entry.
            if (spec == null || spec.entries().size() != 1 || spec.requiredEntries() != 1
                    || !ItemChoiceMatcher.accepts(context.player().registryAccess(), spec, crafted)) return context.progress();
            long required = spec.entries().getFirst().requiredCount();
            return context.progress() >= required ? context.progress()
                    : context.progress() + Math.min((long) crafted.getCount(), required - context.progress());
        }

        @Override
        public Map<String, String> normalizeConfig(
                yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext context,
                Map<String, String> config) {
            // Offline edits keep authored registry data intact; server writes resolve it before commit.
            if (context.registries().isEmpty()) return config;
            var canonical = ItemChoiceMatcher.canonicalEditorConfig(config);
            var checked = ItemChoiceMatcher.normalizeConfig(context.registries().orElseThrow(), canonical);
            var normalized = checked.result().orElseThrow(() -> new IllegalArgumentException(
                    checked.error().map(error -> error.message()).orElse("Item matcher is invalid")));
            var result = new java.util.LinkedHashMap<>(canonical);
            result.put("matcher", normalized.encode());
            result.put("required_entries", Integer.toString(normalized.requiredEntries()));
            return Map.copyOf(result);
        }

        private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
                // Reads can reject unknown static item IDs without decoding dynamic stack components.
                // Enchantment validation still belongs to normalizeConfig with the live server registries.
                .flatXmap(config -> ItemChoiceMatcher.parseConfig(config)
                                .flatMap(ItemChoiceMatcher::validateItemIds).map(ignored -> config),
                        config -> ItemChoiceMatcher.parseConfig(config).map(ignored -> config));

        @Override
        public Map<String, String> creationConfig(Map<String, String> config, Map<String, String> defaults) {
            // Both historical spellings count as explicit input; only omitted consumption inherits.
            if (config.containsKey("consume_items") || config.containsKey("consume")) return config;
            var result = new java.util.LinkedHashMap<>(config);
            if (defaults.containsKey("consume_items")) result.put("consume_items", defaults.get("consume_items"));
            return Map.copyOf(result);
        }

        @Override
        public Codec<Map<String, String>> configCodec() { return CODEC; }

        @Override
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                            .withHelp("Optional objective title"),
                    ConfigFieldDescriptor.field("required_entries", ConfigValueType.INTEGER).withDefault("1")
                            .withRange(1, ItemChoiceMatcher.MAX_CANDIDATES)
                            .withHelp("Number of child item entries required")
                            .withValidator((value, config) -> {
                                ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parse(
                                        config.getOrDefault("matcher", "")).result().orElse(null);
                                int required;
                                try { required = Integer.parseInt(value); }
                                catch (NumberFormatException ignored) { return List.of(); }
                                if (spec == null || required <= spec.entries().size()) return List.of();
                                return List.of(new yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue(
                                        "required_entries",
                                        yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue.Severity.ERROR,
                                        "ENTRY_COUNT", "Required entries exceed the configured item entries"));
                            }),
                    ConfigFieldDescriptor.field("consume_items", ConfigValueType.BOOLEAN).withDefault("false")
                            .withHelp("Consume only the selected entries when submitted"),
                    ConfigFieldDescriptor.field("only_from_crafting", ConfigValueType.BOOLEAN).withDefault("false")
                            .withHelp("Count only items produced by this player in a crafting operation")
                            .withValidator((value, config) -> {
                                // A single stored crafting counter cannot represent several independent entries.
                                var spec = ItemChoiceMatcher.parseConfig(config).result().orElse(null);
                                if (!craftedOnly(config) || spec == null || (spec.entries().size() == 1 && spec.requiredEntries() == 1)) return List.of();
                                return List.of(new yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue("only_from_crafting",
                                        yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue.Severity.ERROR, "BQA-106",
                                        "Crafting-only item objectives require exactly one accepted entry"));
                            }),
                    ConfigFieldDescriptor.field("matcher", ConfigValueType.ITEM_MATCHER).asRequired()
                            .withHelp("Open the child item-property editor")
                            .withValidator((value, config) -> ItemChoiceMatcher.parse(value).error()
                                    .map(error -> List.of(new yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue("matcher",
                                            yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue.Severity.ERROR,
                                            "INVALID_ITEM_MATCHER", error.message()))).orElse(List.of()))
            );
        }

        @Override
        public Map<String, String> editorConfig(yourscraft.jasdewstarfield.brnquest.api.TaskView task) {
            return ItemChoiceMatcher.canonicalEditorConfig(task.config());
        }

        @Override
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            // Inventory matching is readiness, not completion: every item objective needs its own receipt.
            return context.progress() >= (craftedOnly(config) ? craftingRequired(config) : 1);
        }

        @Override
        public boolean consume(TaskContext context, Map<String, String> config) {
            // Whole-quest completion must never choose inventory slots on behalf of the player.
            // Consumption belongs exclusively to submit(), inside the single-objective transaction.
            return context.progress() >= 1;
        }

        @Override
        public TaskSubmissionResult submit(TaskContext context, Map<String, String> config,
                                           TaskSubmissionSelection selection) {
            if (craftedOnly(config)) {
                return TaskSubmissionResult.failure("CRAFTING_ONLY", "This objective advances only from crafting events");
            }
            ItemChoiceMatcher.MatchPlan plan = plan(context, config, selection);
            if (plan == null || !plan.satisfied()) {
                return TaskSubmissionResult.failure("UNSATISFIED", "Item objective requirements are incomplete");
            }
            if (!plan.selectionValid()) {
                return TaskSubmissionResult.failure("INVALID_ITEM_SELECTION",
                        "Selected item entries are incomplete or no longer available");
            }
            if (consumesItems(config) && !ItemChoiceMatcher.consume(context.player().getInventory().items, plan)) {
                return TaskSubmissionResult.failure("CONSUME_FAILED", "Selected item entries could not be consumed");
            }
            return TaskSubmissionResult.accepted();
        }

        @Override
        public boolean allowsManualSubmission(Map<String, String> config) { return !craftedOnly(config); }

        @Override
        public boolean reevaluateOnInventoryChange(Map<String, String> config) {
            // Holding an item completes the objective without a click; consuming and crafting-only
            // objectives retain their explicit submission and crafting provenance paths.
            return !consumesItems(config) && !craftedOnly(config);
        }

        @Override
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task,
                                  Map<String, String> config) {
            String title = config.getOrDefault("title", "");
            return Component.literal(title.isBlank() ? "Item objective" : title);
        }

        private ItemChoiceMatcher.MatchPlan plan(TaskContext context, Map<String, String> config,
                                                 TaskSubmissionSelection selection) {
            ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parseConfig(config).result().orElse(null);
            return spec == null ? null : ItemChoiceMatcher.plan(context.player().registryAccess(),
                    context.player().getInventory().items, spec, selection.inventorySlots());
        }

        private boolean consumesItems(Map<String, String> config) {
            String value = config.getOrDefault("consume_items", "");
            if (value.isBlank()) value = config.getOrDefault("consume", "false");
            return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
        }

        private static boolean craftedOnly(Map<String, String> config) {
            return Boolean.parseBoolean(config.getOrDefault("only_from_crafting", "false").replace("1b", "true"));
        }

        private long craftingRequired(Map<String, String> config) {
            return ItemChoiceMatcher.parseConfig(config).result().map(spec -> (long) spec.entries().stream()
                    .mapToInt(ItemChoiceMatcher.Entry::requiredCount).min().orElse(1)).orElse(1L);
        }
    }

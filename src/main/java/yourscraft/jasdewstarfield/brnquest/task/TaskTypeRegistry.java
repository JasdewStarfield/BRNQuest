package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.runtime.ScriptExtensionRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible task registry with a construction-time registration window. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class TaskTypeRegistry {
    private static final Map<ResourceLocation, TaskType<?>> TYPES = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    static {
        yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterTargets.register();
        yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterEvents.initialize();
        register(yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.OBSERVE,new yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterTask(true));
        register(yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.KILL,new yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterTask(false));
        yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementFieldSource.register();
        register(yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementConfig.ID,
                new yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementTask());
        yourscraft.jasdewstarfield.brnquest.task.location.LocationFieldSources.register();
        for (String kind : java.util.List.of("dimension", "biome", "location", "structure"))
            register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("brnquest", kind), new yourscraft.jasdewstarfield.brnquest.task.location.LocationTask(kind));

        register(TaskTypes.CHECKMARK, new CheckmarkTask());
        register(TaskTypes.CUSTOM, new ProgressTask());
        register(TaskTypes.ITEM, new UnifiedItemTask());
        // The historical type ID remains readable, but both IDs now share one model and editor.
        register(TaskTypes.ITEM_CHOICE, new UnifiedItemTask());
        register(TaskTypes.XP, new ExperienceTask());
    }

    private TaskTypeRegistry() {}

    /**
     * Registers a type during mod construction or common setup.
     * The registry is frozen before the first server resource reload.
     */
    public static synchronized void register(ResourceLocation id, TaskType<?> type) {
        if (frozen) throw new IllegalStateException("Task type registry is already frozen");
        if (TYPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Task type is already registered: " + id);
        }
    }

    public static TaskType<?> get(ResourceLocation id) {
        TaskType<?> common = TYPES.get(id);
        return common != null ? common : ScriptExtensionRegistry.task(id);
    }

    /** Returns an immutable snapshot for authoring UIs without exposing the live registry map. */
    @ApiStatus(ApiStability.INTERNAL)
    public static Set<ResourceLocation> registeredIds() {
        Set<ResourceLocation> ids = new java.util.HashSet<>(TYPES.keySet());
        ScriptExtensionRegistry.snapshot().taskTypeIds().stream()
                .map(ResourceLocation::parse).forEach(ids::add);
        return Set.copyOf(ids);
    }

    /** Script batches may replace their own IDs but can never shadow construction-time Java types. */
    @ApiStatus(ApiStability.INTERNAL)
    public static boolean isCommonRegistered(ResourceLocation id) { return TYPES.containsKey(id); }

    /** Closes the public registration window before task books are decoded. */
    public static synchronized void freeze() { frozen = true; }

    public static boolean isFrozen() { return frozen; }

    private static final class CheckmarkTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                    .withHelp("Optional objective title"));
        }
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= 1;
        }
        public boolean allowsManualSubmission(Map<String, String> config) { return true; }
        public boolean acceptsQuestCompletionIntent(Map<String, String> config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, Map<String, String> config) {
            return TaskSubmissionResult.accepted();
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            return Component.literal(config.getOrDefault("title", task.typeId().toString()));
        }
    }

    private static final class ProgressTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= 1;
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            return Component.literal(config.getOrDefault("title", task.typeId().toString()));
        }
    }

    /** Both historical IDs decode through this canonical target-plus-child-entry model. */
    private static final class UnifiedItemTask implements TaskType<Map<String, String>> {
        private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
                .flatXmap(config -> ItemChoiceMatcher.normalizeConfig(
                                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), config)
                                .map(ignored -> config),
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
                            .withHelp("Count only items produced by this player in a crafting operation"),
                    ConfigFieldDescriptor.field("matcher", ConfigValueType.ITEM_MATCHER).asRequired()
                            .withHelp("Open the child item-property editor")
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
        public boolean reevaluateOnInventoryChange(Map<String, String> config) { return false; }

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

        private boolean craftedOnly(Map<String, String> config) {
            return Boolean.parseBoolean(config.getOrDefault("only_from_crafting", "false").replace("1b", "true"));
        }

        private long craftingRequired(Map<String, String> config) {
            return ItemChoiceMatcher.parseConfig(config).result().map(spec -> (long) spec.entries().stream()
                    .mapToInt(ItemChoiceMatcher.Entry::requiredCount).min().orElse(1)).orElse(1L);
        }
    }

    /** Submits either raw experience points or whole levels in one server-side transaction. */
    private static final class ExperienceTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("value", ConfigValueType.INTEGER).withDefault("1").withRange(1, Integer.MAX_VALUE)
                            .withHelp("Experience amount to submit"),
                    ConfigFieldDescriptor.field("points", ConfigValueType.BOOLEAN).withDefault("true")
                            .withHelp("Use raw experience points instead of whole levels"));
        }
        public boolean satisfied(TaskContext context, Map<String, String> config) { return context.progress() >= 1; }
        public boolean allowsManualSubmission(Map<String, String> config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, Map<String, String> config) {
            int value;
            try { value = Integer.parseInt(config.getOrDefault("value", "1").replaceAll("[^0-9-]", "")); }
            catch (NumberFormatException exception) { return TaskSubmissionResult.failure("INVALID_XP", "Invalid experience amount"); }
            if (value < 1) return TaskSubmissionResult.failure("INVALID_XP", "Experience amount must be positive");
            boolean points = Boolean.parseBoolean(config.getOrDefault("points", "true").replace("1b", "true"));
            if (points && context.player().totalExperience < value) return TaskSubmissionResult.failure("INSUFFICIENT_XP", "Not enough experience points");
            if (!points && context.player().experienceLevel < value) return TaskSubmissionResult.failure("INSUFFICIENT_XP", "Not enough experience levels");
            if (points) context.player().giveExperiencePoints(-value); else context.player().giveExperienceLevels(-value);
            return TaskSubmissionResult.accepted();
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            boolean points = Boolean.parseBoolean(config.getOrDefault("points", "true").replace("1b", "true"));
            return Component.literal(config.getOrDefault("value", "1") + (points ? " experience points" : " levels"));
        }
    }
}

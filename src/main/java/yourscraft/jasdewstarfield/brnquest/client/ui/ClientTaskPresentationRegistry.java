package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Client presentation registry; registration closes during client setup. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ClientTaskPresentationRegistry {
    private static final Map<ResourceLocation, ClientTaskPresentation> PRESENTATIONS = new ConcurrentHashMap<>();
    private static final ClientTaskPresentation FALLBACK = new ClientTaskPresentation() {};
    private static volatile boolean frozen;

    static {
        for (String kind : java.util.List.of("dimension", "biome", "location", "structure"))
            register(ResourceLocation.fromNamespaceAndPath("brnquest", kind), new LocationTaskPresentation(kind));
        register(TaskTypes.CHECKMARK, new CheckmarkPresentation());
        register(TaskTypes.ITEM, new ItemChoicePresentation());
        register(TaskTypes.ITEM_CHOICE, new ItemChoicePresentation());
        register(TaskTypes.XP, new ClientTaskPresentation() {
            public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
            public String symbol(TaskView task) { return "✦"; }
            public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task.xp"); }
            public boolean interactive(TaskView task) { return true; }
            public boolean satisfied(TaskPresentationContext context) { return context.storedProgress() >= 1; }
            public boolean readyForSubmission(TaskPresentationContext context) {
                return context.minecraft().player != null && experienceAvailable(context.task(),
                        context.minecraft().player.totalExperience, context.minecraft().player.experienceLevel);
            }
            public Component title(TaskPresentationContext context) {
                boolean points = usesRawExperiencePoints(context.task());
                return Component.translatable(points ? "screen.brnquest.task.xp.points" : "screen.brnquest.task.xp.levels",
                        context.task().config().getOrDefault("value", "1"));
            }
        });
        register(TaskTypes.CUSTOM, new ClientTaskPresentation() {
            public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
            public String symbol(TaskView task) { return "◆"; }
            public Component typeName(TaskView task) {
                return Component.translatable("screen.brnquest.type.task.custom");
            }
        });
    }

    private ClientTaskPresentationRegistry() {}

    /** Registers a client view during mod construction, before client setup freezes the registry. */
    public static synchronized void register(ResourceLocation id, ClientTaskPresentation presentation) {
        if (frozen) throw new IllegalStateException("Client task presentation registry is already frozen");
        if (PRESENTATIONS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(presentation)) != null) {
            throw new IllegalArgumentException("Client task presentation is already registered: " + id);
        }
    }

    public static ClientTaskPresentation get(ResourceLocation id) {
        return PRESENTATIONS.getOrDefault(id, FALLBACK);
    }

    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }

    /** Shared receipt rule for legacy and unified item presentations, independent of a running client. */
    static boolean itemObjectiveSubmitted(TaskView task, long storedProgress) {
        return booleanConfig(task, "only_from_crafting")
                ? storedProgress >= requiredCount(task)
                : storedProgress >= 1;
    }

    /** Schema-1 counts remain strings, so presentation parsing mirrors the authoritative codec bounds. */
    static int requiredCount(TaskView task) {
        ItemChoiceMatcher.Spec spec = itemSpec(task);
        if (spec != null && spec.entries().size() == 1) return spec.entries().getFirst().requiredCount();
        String raw = task.config().getOrDefault("count", "1");
        try {
            long parsed = Long.parseLong(raw.replaceAll("[^0-9-]", ""));
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, parsed));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    static ItemChoiceMatcher.Spec itemSpec(TaskView task) {
        return ItemChoiceMatcher.parseConfig(task.config()).result().orElse(null);
    }

    /** Mirrors the server's XP balance check so yellow readiness never promises a rejected submission. */
    static boolean experienceAvailable(TaskView task, int totalExperience, int experienceLevel) {
        int required;
        try {
            required = Integer.parseInt(task.config().getOrDefault("value", "1").replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (required < 1) return false;
        return usesRawExperiencePoints(task) ? totalExperience >= required : experienceLevel >= required;
    }

    /** Multi-entry objectives describe the required number of types, never a misleading stack count. */
    static Component multipleItemObjectiveTitle(TaskView task, ItemChoiceMatcher.Spec spec) {
        String key = consumesItems(task)
                ? "screen.brnquest.task.item_choice.require"
                : "screen.brnquest.task.item_choice.hold";
        return Component.translatable(key, spec.entries().size(), spec.requiredEntries());
    }

    /** Mirrors the authoritative codec's canonical field and legacy alias for client wording. */
    static boolean consumesItems(TaskView task) {
        String canonical = task.config().getOrDefault("consume_items", "");
        String value = canonical.isBlank() ? task.config().getOrDefault("consume", "false") : canonical;
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    /** Short underlined qualifier used by built-in item objectives in the detail row. */
    static Component itemObjectiveQualifier(TaskView task) {
        if (craftingOnly(task)) {
            return Component.translatable("screen.brnquest.task.item.crafting.label");
        }
        return Component.translatable(consumesItems(task)
                ? "screen.brnquest.task.item.require.label"
                : "screen.brnquest.task.item.hold.label");
    }

    /** Explains whether satisfying the built-in item objective consumes matching inventory. */
    static Component itemObjectiveQualifierHint(TaskView task) {
        if (craftingOnly(task)) {
            return Component.translatable("screen.brnquest.task.item.crafting.hint");
        }
        return Component.translatable(consumesItems(task)
                ? "screen.brnquest.task.item.require.hint"
                : "screen.brnquest.task.item.hold.hint");
    }

    /** Generated item name without an author override, used as the override's explanatory Tooltip. */
    static Component defaultItemObjectiveTitle(TaskPresentationContext context) {
        ItemChoiceMatcher.Spec spec = itemSpec(context.task());
        if (spec != null && spec.entries().size() > 1) {
            return multipleItemObjectiveTitle(context.task(), spec);
        }
        Component itemTitle;
        if (spec != null && spec.entries().getFirst().kind() == ItemChoiceMatcher.EntryKind.TAG) {
            itemTitle = Component.translatable("screen.brnquest.task.item_choice.tag_title");
        } else if (spec != null && spec.entries().size() == 1
                && context.displayedItem() != null && !context.displayedItem().isEmpty()) {
            itemTitle = context.displayedItem().getHoverName();
        } else {
            itemTitle = Component.translatable("screen.brnquest.task.item_choice.list_title",
                    spec == null ? 0 : spec.entries().size());
        }
        String key = consumesItems(context.task())
                ? "screen.brnquest.task.item.require" : "screen.brnquest.task.item.hold";
        return Component.translatable(key, itemTitle, requiredCount(context.task()));
    }

    private static final class CheckmarkPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CHECKMARK; }
        public String symbol(TaskView task) { return "✓"; }
        public Component typeName(TaskView task) {
            return Component.translatable("screen.brnquest.type.task.checkmark");
        }
        public boolean interactive(TaskView task) { return true; }
        public boolean acceptsQuestCompletionIntent(TaskView task) { return true; }
        public Component progressText(TaskPresentationContext context, boolean satisfied) {
            return Component.translatable(satisfied ? "screen.brnquest.task.checked" : "screen.brnquest.task.manual");
        }
        public Component title(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            return configured.isBlank() ? Component.translatable("screen.brnquest.task.checkmark") : Component.literal(configured);
        }
    }

    private static final class ItemPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.ITEM; }
        public String itemSnbt(TaskView task) { return task.config().getOrDefault("item", ""); }
        public Component typeName(TaskView task) {
            return Component.translatable("screen.brnquest.type.task.item");
        }
        public boolean interactive(TaskView task) { return true; }

        public boolean satisfied(TaskPresentationContext context) {
            // The quest-wide submit affordance must agree with the server's receipt-only rule.
            return itemObjectiveSubmitted(context.task(), context.storedProgress());
        }

        public Component progressText(TaskPresentationContext context, boolean satisfied) {
            if (!context.displayedItem().isEmpty() && context.minecraft().player != null) {
                return Component.literal(present(context.minecraft(), context.displayedItem())
                        + " / " + requiredCount(context.task()));
            }
            return ClientTaskPresentation.super.progressText(context, satisfied);
        }

        public boolean readyForSubmission(TaskPresentationContext context) {
            return !context.displayedItem().isEmpty() && context.minecraft().player != null
                    && present(context.minecraft(), context.displayedItem()) >= requiredCount(context.task());
        }

        public Component title(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            if (!configured.isBlank()) return Component.literal(configured);
            return context.displayedItem().isEmpty()
                    ? Component.literal(context.task().typeId().toString())
                    : context.displayedItem().getHoverName();
        }

        public Component objectiveTitle(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            if (!configured.isBlank()) return Component.literal(configured);
            ItemChoiceMatcher.Spec spec = itemSpec(context.task());
            if (spec != null && spec.entries().size() > 1) {
                return multipleItemObjectiveTitle(context.task(), spec);
            }
            String key = ClientTaskPresentationRegistry.consumesItems(context.task())
                    ? "screen.brnquest.task.item.require"
                    : "screen.brnquest.task.item.hold";
            return Component.translatable(key, title(context), requiredCount(context.task()));
        }

        private int present(Minecraft minecraft, ItemStack expected) {
            return minecraft.player.getInventory().items.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum();
        }

    }

    private static final class ItemChoicePresentation implements ClientTaskPresentation {
        @Override
        public NodeStyle nodeStyle(TaskView task) {
            ItemChoiceMatcher.Spec spec = spec(task);
            return spec != null && spec.entries().getFirst().kind() == ItemChoiceMatcher.EntryKind.ITEM
                    ? NodeStyle.ITEM : NodeStyle.CUSTOM;
        }

        @Override
        public String itemSnbt(TaskView task) {
            ItemChoiceMatcher.Spec spec = spec(task);
            return spec != null && spec.entries().getFirst().kind() == ItemChoiceMatcher.EntryKind.ITEM
                    ? spec.entries().getFirst().value() : "";
        }

        @Override
        public String symbol(TaskView task) {
            return "◇";
        }

        @Override
        public Component typeName(TaskView task) {
            return Component.translatable(task.typeId().equals(TaskTypes.ITEM)
                    ? "screen.brnquest.type.task.item" : "screen.brnquest.type.task.item_choice");
        }

        @Override
        public boolean interactive(TaskView task) { return !craftingOnly(task); }

        @Override
        public ItemStack displayedItem(TaskPresentationContext context) {
            ItemChoiceMatcher.MatchPlan plan = plan(context);
            return plan == null ? ItemStack.EMPTY : plan.representative();
        }

        @Override
        public List<ItemStack> acceptedItems(TaskPresentationContext context) {
            ItemChoiceMatcher.Spec spec = spec(context.task());
            if (spec == null || context.minecraft().level == null) return List.of();
            return ItemChoiceMatcher.displayedCandidates(context.minecraft().level.registryAccess(), spec);
        }

        @Override
        public boolean hasCandidateMenu(TaskPresentationContext context) {
            return spec(context.task()) != null;
        }

        @Override
        public boolean satisfied(TaskPresentationContext context) {
            // Readiness still comes from the match plan, but only a receipt satisfies the quest.
            return craftingOnly(context.task())
                    ? context.storedProgress() >= requiredCount(context.task())
                    : itemObjectiveSubmitted(context.task(), context.storedProgress());
        }

        @Override
        public boolean readyForSubmission(TaskPresentationContext context) {
            if (craftingOnly(context.task())) return false;
            ItemChoiceMatcher.MatchPlan plan = plan(context);
            return plan != null && plan.satisfied();
        }

        @Override
        public Component progressText(TaskPresentationContext context, boolean satisfied) {
            if (craftingOnly(context.task())) {
                return Component.translatable("screen.brnquest.task.crafted.progress",
                        Math.min(context.storedProgress(), requiredCount(context.task())), requiredCount(context.task()));
            }
            ItemChoiceMatcher.MatchPlan plan = plan(context);
            if (plan == null) return ClientTaskPresentation.super.progressText(context, satisfied);
            return Component.translatable("screen.brnquest.task.item_choice.progress",
                    Math.min(plan.satisfiedEntries(), plan.requiredEntries()), plan.requiredEntries());
        }

        @Override
        public Component title(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            if (!configured.isBlank()) return Component.literal(configured);
            ItemChoiceMatcher.Spec spec = spec(context.task());
            if (spec != null && spec.entries().getFirst().kind() == ItemChoiceMatcher.EntryKind.TAG) {
                return Component.translatable("screen.brnquest.task.item_choice.tag_title");
            }
            int candidates = spec == null ? 0 : spec.entries().size();
            if (candidates == 1 && context.displayedItem() != null && !context.displayedItem().isEmpty()) {
                return context.displayedItem().getHoverName();
            }
            return Component.translatable("screen.brnquest.task.item_choice.list_title", candidates);
        }

        @Override
        public Component objectiveTitle(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            if (!configured.isBlank()) return Component.literal(configured);
            ItemChoiceMatcher.Spec spec = spec(context.task());
            if (spec != null && spec.entries().size() > 1) {
                return multipleItemObjectiveTitle(context.task(), spec);
            }
            String key = ClientTaskPresentationRegistry.consumesItems(context.task())
                    ? "screen.brnquest.task.item.require"
                    : "screen.brnquest.task.item.hold";
            return Component.translatable(key, title(context), requiredCount(context.task()));
        }

        private ItemChoiceMatcher.MatchPlan plan(TaskPresentationContext context) {
            ItemChoiceMatcher.Spec spec = spec(context.task());
            if (spec == null || context.minecraft().level == null || context.minecraft().player == null) return null;
            return ItemChoiceMatcher.plan(context.minecraft().level.registryAccess(),
                    context.minecraft().player.getInventory().items, spec);
        }

        private ItemChoiceMatcher.Spec spec(TaskView task) {
            return itemSpec(task);
        }
    }

    private static boolean booleanConfig(TaskView task, String key) {
        String value = task.config().getOrDefault(key, "false");
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    static boolean craftingOnly(TaskView task) {
        return booleanConfig(task, "only_from_crafting");
    }

    /** Internal receipt interpretation; counted crafting rows require their full configured amount. */
    static boolean confirmed(TaskView task, long storedProgress) {
        return task.typeId().equals(TaskTypes.ITEM) || task.typeId().equals(TaskTypes.ITEM_CHOICE)
                ? itemObjectiveSubmitted(task, storedProgress)
                : storedProgress >= 1;
    }

    private static boolean usesRawExperiencePoints(TaskView task) {
        String value = task.config().getOrDefault("points", "true");
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }
}

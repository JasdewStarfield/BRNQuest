package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

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
        register(TaskTypes.CHECKMARK, new CheckmarkPresentation());
        register(TaskTypes.ITEM, new ItemPresentation());
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

    /** Schema-1 counts remain strings, so presentation parsing mirrors the authoritative codec bounds. */
    static int requiredCount(TaskView task) {
        String raw = task.config().getOrDefault("count", "1");
        try {
            long parsed = Long.parseLong(raw.replaceAll("[^0-9-]", ""));
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, parsed));
        } catch (NumberFormatException ignored) {
            return 1;
        }
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
            if (ClientTaskPresentation.super.satisfied(context)) return true;
            if (context.displayedItem().isEmpty() || context.minecraft().player == null) return false;
            return present(context.minecraft(), context.displayedItem()) >= requiredCount(context.task());
        }

        public Component progressText(TaskPresentationContext context, boolean satisfied) {
            if (!context.displayedItem().isEmpty() && context.minecraft().player != null) {
                return Component.literal(present(context.minecraft(), context.displayedItem())
                        + " / " + requiredCount(context.task()));
            }
            return ClientTaskPresentation.super.progressText(context, satisfied);
        }

        public Component title(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            if (!configured.isBlank()) return Component.literal(configured);
            return context.displayedItem().isEmpty()
                    ? Component.literal(context.task().typeId().toString())
                    : context.displayedItem().getHoverName();
        }

        private int present(Minecraft minecraft, ItemStack expected) {
            return minecraft.player.getInventory().items.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum();
        }
    }
}

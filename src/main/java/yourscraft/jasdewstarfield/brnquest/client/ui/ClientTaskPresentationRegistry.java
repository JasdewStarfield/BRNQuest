package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
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
            public NodeStyle nodeStyle(TaskDefinition task) { return NodeStyle.CUSTOM; }
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
    static int requiredCount(TaskDefinition task) {
        String raw = task.config().getOrDefault("count", "1");
        try {
            long parsed = Long.parseLong(raw.replaceAll("[^0-9-]", ""));
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, parsed));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static final class CheckmarkPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskDefinition task) { return NodeStyle.CHECKMARK; }
        public String symbol(TaskDefinition task) { return "✓"; }
        public boolean interactive(TaskDefinition task) { return true; }
        public boolean acceptsQuestCompletionIntent(TaskDefinition task) { return true; }
        public Component progressText(Minecraft minecraft, TaskDefinition task, boolean satisfied,
                                      long storedProgress, ItemStack displayedItem) {
            return Component.translatable(satisfied ? "screen.brnquest.task.checked" : "screen.brnquest.task.manual");
        }
        public Component fallbackTitle(Minecraft minecraft, TaskDefinition task, ItemStack displayedItem) {
            String configured = task.config().getOrDefault("title", "");
            return configured.isBlank() ? Component.translatable("screen.brnquest.task.checkmark") : Component.literal(configured);
        }
    }

    private static final class ItemPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskDefinition task) { return NodeStyle.ITEM; }
        public String itemSnbt(TaskDefinition task) { return task.config().getOrDefault("item", ""); }
        public boolean interactive(TaskDefinition task) { return true; }

        public boolean satisfied(Minecraft minecraft, TaskDefinition task, QuestStatus status,
                                 long storedProgress, ItemStack displayedItem) {
            if (ClientTaskPresentation.super.satisfied(minecraft, task, status, storedProgress, displayedItem)) return true;
            if (displayedItem.isEmpty() || minecraft.player == null) return false;
            return present(minecraft, displayedItem) >= requiredCount(task);
        }

        public Component progressText(Minecraft minecraft, TaskDefinition task, boolean satisfied,
                                      long storedProgress, ItemStack displayedItem) {
            if (!displayedItem.isEmpty() && minecraft.player != null) {
                return Component.literal(present(minecraft, displayedItem) + " / " + requiredCount(task));
            }
            return ClientTaskPresentation.super.progressText(minecraft, task, satisfied, storedProgress, displayedItem);
        }

        public Component fallbackTitle(Minecraft minecraft, TaskDefinition task, ItemStack displayedItem) {
            String configured = task.config().getOrDefault("title", "");
            if (!configured.isBlank()) return Component.literal(configured);
            return displayedItem.isEmpty() ? Component.literal(task.typeId().toString()) : displayedItem.getHoverName();
        }

        private int present(Minecraft minecraft, ItemStack expected) {
            return minecraft.player.getInventory().items.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum();
        }
    }
}

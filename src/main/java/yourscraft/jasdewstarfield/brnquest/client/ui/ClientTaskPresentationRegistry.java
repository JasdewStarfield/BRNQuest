package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Client presentation registry; registration closes during client setup. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ClientTaskPresentationRegistry {
    private static final Map<ResourceLocation, ClientTaskPresentation> PRESENTATIONS = new ConcurrentHashMap<>();
    private static final ClientTaskPresentation FALLBACK = new ClientTaskPresentation() {};
    private static final Map<ResourceLocation, yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> TYPE_ICONS = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    private ClientTaskPresentationRegistry() {}

    /** Registers a client view during mod construction, before client setup freezes the registry. */
    public static synchronized void register(ResourceLocation id, ClientTaskPresentation presentation) {
        if (frozen) throw new IllegalStateException("Client task presentation registry is already frozen");
        if (PRESENTATIONS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(presentation)) != null) {
            throw new IllegalArgumentException("Client task presentation is already registered: " + id);
        }
    }

    /** Optional registration metadata uses the same lifecycle and duplicate checks as presentations. */
    public static synchronized void register(ResourceLocation id, ClientTaskPresentation presentation,
            yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon icon) {
        Objects.requireNonNull(icon);
        register(id, presentation);
        TYPE_ICONS.put(id, icon);
    }

    /** No synthetic instance/config is required to render a type choice. */
    public static yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon typeIcon(ResourceLocation id) {
        return get(id).typeIcon().orElseGet(() -> TYPE_ICONS.getOrDefault(id, ClientTypeIconFallback.icon()));
    }
    /** Keeps unknown types on their fallback glyph instead of inventing a registered decoration. */
    static boolean hasTypeIcon(ResourceLocation id) { return get(id).typeIcon().isPresent() || TYPE_ICONS.containsKey(id); }
    /** An installed legacy addon may have a symbol but no type sprite. */
    static boolean hasPresentation(ResourceLocation id) { return PRESENTATIONS.containsKey(id); }
    public static ClientTaskPresentation get(ResourceLocation id) {
        return PRESENTATIONS.getOrDefault(id, FALLBACK);
    }

    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }

    static boolean confirmed(TaskView task, long storedProgress) {
        return get(task.typeId()).confirmed(task, storedProgress);
    }
}

package yourscraft.jasdewstarfield.brnquest.event;

import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Isolated observation bus: one failing listener never prevents later listeners from running. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class BrnQuestEvents {
    private static final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> LISTENERS = new ConcurrentHashMap<>();

    private BrnQuestEvents() {}

    public static <T extends BrnQuestEvent> EventSubscription subscribe(Class<T> eventType, Consumer<T> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        CopyOnWriteArrayList<Consumer<?>> listeners = LISTENERS.computeIfAbsent(eventType,
                ignored -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Runtime publication is public only across package boundaries and is not an integration API. */
    @ApiStatus(ApiStability.INTERNAL)
    public static <T extends BrnQuestEvent> void post(T event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<?> rawListener : LISTENERS.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>())) {
            try {
                @SuppressWarnings("unchecked") Consumer<T> listener = (Consumer<T>) rawListener;
                listener.accept(event);
            } catch (RuntimeException | LinkageError exception) {
                BRNQuest.LOGGER.error("[BRNQuest/EVENT] Listener failed for {}; continuing delivery",
                        event.getClass().getSimpleName(), exception);
            }
        }
    }
}

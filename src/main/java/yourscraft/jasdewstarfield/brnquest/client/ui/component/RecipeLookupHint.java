package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Optional recipe-viewer hint bridge. Core screens can render a hint without resolving
 * any JEI class when that optional mod is absent.
 */
public final class RecipeLookupHint {
    private static final Supplier<Optional<Component>> EMPTY = Optional::empty;
    private static volatile Supplier<Optional<Component>> provider = EMPTY;

    private RecipeLookupHint() {}

    public static Optional<Component> current() {
        return provider.get();
    }

    public static void install(Supplier<Optional<Component>> hintProvider) {
        provider = hintProvider == null ? EMPTY : hintProvider;
    }

    public static void clear() {
        provider = EMPTY;
    }
}

package yourscraft.jasdewstarfield.brnquest;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Run fixture wiring in the same transformed loader as the actual NeoForge test class. */
public final class BuiltinTestBootstrap implements BeforeAllCallback {
    public void beforeAll(ExtensionContext context) throws Exception {
        // ServiceLoader discovers extensions in the launcher loader, whose static registries are separate.
        Class.forName("yourscraft.jasdewstarfield.brnquest.BuiltinTestRuntime", true,
                context.getRequiredTestClass().getClassLoader()).getMethod("initialize").invoke(null);
    }
}

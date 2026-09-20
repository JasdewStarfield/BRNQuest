package yourscraft.jasdewstarfield.brnquest;

import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.builtin.basic.BuiltinBasicPlugin;
import yourscraft.jasdewstarfield.brnquest.builtin.basic.client.BuiltinBasicClient;

/** Test-only physical-client setup for the server-oriented NeoForge unit harness. */
public final class BuiltinTestRuntime {
    private static boolean initialized;
    private BuiltinTestRuntime() {}
    public static synchronized void initialize() {
        if (initialized) return;
        var plugin = new BuiltinBasicPlugin();
        // NeoForge may already have constructed the common mod in this loader.
        if (!BrnQuestPlugins.registeredPluginIds().contains(plugin.id())) yourscraft.jasdewstarfield.brnquest.builtin.BuiltinPlugins.register();
        BuiltinBasicClient.register();
        yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinClientTypes.register();
        initialized = true;
    }
}

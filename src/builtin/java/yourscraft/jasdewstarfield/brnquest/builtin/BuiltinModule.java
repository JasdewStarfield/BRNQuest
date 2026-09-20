package yourscraft.jasdewstarfield.brnquest.builtin;

import yourscraft.jasdewstarfield.brnquest.platform.RuntimeModule;

/** Service provider owns common registration; the core has no built-in implementation dependency. */
public final class BuiltinModule implements RuntimeModule {
    @Override public void register() { BuiltinPlugins.register(); }
}

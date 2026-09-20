package yourscraft.jasdewstarfield.brnquest.builtin;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.builtin.basic.BuiltinBasicPlugin;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterEvents;
/** Loader composition root for built-in plugins; generic registries do not discover implementations. */
public final class BuiltinPlugins {
    private BuiltinPlugins() {}
    public static void register() {
        BrnQuestPlugins.register(new BuiltinBasicPlugin());
        BrnQuestPlugins.register(new BuiltinObservationPlugin());
        BrnQuestPlugins.register(new BuiltinItemPlugin());
        BrnQuestPlugins.register(new BuiltinExecutionPlugin());
        BrnQuestPlugins.register(new BuiltinCompositionPlugin());
        // Event subscriptions follow successful declarations; each plugin owns its caches.
        EncounterEvents.initialize();
    }
}

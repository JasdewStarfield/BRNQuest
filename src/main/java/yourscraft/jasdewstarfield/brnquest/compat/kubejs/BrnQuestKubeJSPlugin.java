package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.runtime.ScriptExtensionRegistry;

/** Isolated KubeJS discovery entry point; core initialization never references this class. */
public final class BrnQuestKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void init() {
        BrnQuestKubeJSEventBridge.install();
    }

    @Override
    public void registerEvents(EventGroupRegistry events) {
        events.register(BrnQuestKubeJSEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // Gameplay mutations are server-authoritative, so startup and client scripts get no binding.
        if (bindings.type() == ScriptType.SERVER) {
            bindings.add("BRNQuest", BrnQuestKubeJSBindings.INSTANCE);
        }
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) ScriptExtensionRegistry.beginRegistration();
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;
        if (!manager.scriptType.console.errors.isEmpty()) {
            ScriptExtensionRegistry.failRegistration("KubeJS server scripts contain errors");
            BRNQuest.LOGGER.error("[BRNQuest/KUBEJS] Server scripts contain errors; preserving prior script type snapshot");
            return;
        }
        try {
            // The task-book reload listener validates against this sealed candidate and commits
            // both sides together; no script-defined Java object is exposed to the old snapshot.
            ScriptExtensionRegistry.sealRegistration();
        } catch (RuntimeException exception) {
            ScriptExtensionRegistry.failRegistration("BRNQuest script type registration failed");
            // Report through KubeJS as well as the mod log so the originating script workflow sees the failure.
            manager.scriptType.console.error("BRNQuest script type registration failed", exception);
        }
    }
}

package yourscraft.jasdewstarfield.brnquest.platform;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brnquest.client.ClientKeyRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.QuestHud;

/** Client-only registrations are guarded at the loader boundary for dedicated-server safety. */
public final class PlatformClientHooks {
    private PlatformClientHooks() {}
    public static void register(IEventBus bus) {
        bus.addListener(PlatformClientHooks::keys);
        bus.addListener(PlatformClientHooks::layers);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::tick);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(ClientKeyRegistry.create()); }
    private static void layers(RegisterGuiLayersEvent event) { event.registerAbove(VanillaGuiLayers.CHAT, QuestHud.LAYER_ID, QuestHud::render); }
    private static void tick(ClientTickEvent.Post event) { ClientKeyRegistry.tick(); }
}

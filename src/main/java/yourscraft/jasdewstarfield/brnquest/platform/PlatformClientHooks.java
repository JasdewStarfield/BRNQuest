package yourscraft.jasdewstarfield.brnquest.platform;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brnquest.client.ClientKeyRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.QuestHud;
import yourscraft.jasdewstarfield.brnquest.runtime.ExtensionRegistrationLifecycle;

/** Client-only registrations are guarded at the loader boundary for dedicated-server safety. */
public final class PlatformClientHooks {
    private PlatformClientHooks() {}
    public static void register(IEventBus bus) {
        bus.addListener(PlatformClientHooks::setup);
        bus.addListener(PlatformClientHooks::keys);
        bus.addListener(PlatformClientHooks::layers);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::tick);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::logout);
        NeoForge.EVENT_BUS.addListener(yourscraft.jasdewstarfield.brnquest.client.ClientHealthCommand::register);
    }
    private static void setup(FMLClientSetupEvent event) {
        // Client extensions register during construction; rendering starts only after this freeze.
        ClientTaskPresentationRegistry.freeze();
        ClientRewardPresentationRegistry.freeze();
        ExtensionRegistrationLifecycle.markClientFrozen();
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(ClientKeyRegistry.create()); }
    private static void layers(RegisterGuiLayersEvent event) { event.registerAbove(VanillaGuiLayers.CHAT, QuestHud.LAYER_ID, QuestHud::render); }
    private static void tick(ClientTickEvent.Post event) { ClientKeyRegistry.tick(); }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        yourscraft.jasdewstarfield.brnquest.client.ui.RewardTableClientState.clear();
        ClientEditorState.get().disconnected();
        yourscraft.jasdewstarfield.brnquest.client.ClientQuestState.get().disconnected();
    }
}

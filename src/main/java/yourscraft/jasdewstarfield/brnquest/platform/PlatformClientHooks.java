package yourscraft.jasdewstarfield.brnquest.platform;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brnquest.client.ClientKeyRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.QuestHud;
import yourscraft.jasdewstarfield.brnquest.client.ui.InventoryQuestButton;
import yourscraft.jasdewstarfield.brnquest.runtime.ExtensionRegistrationLifecycle;

/** Client-only registrations are guarded at the loader boundary for dedicated-server safety. */
public final class PlatformClientHooks {
    private PlatformClientHooks() {}
    public static void register(IEventBus bus) {
        yourscraft.jasdewstarfield.brnquest.client.ClientModule.registerInstalled();
        bus.addListener(PlatformClientHooks::setup);
        bus.addListener(PlatformClientHooks::keys);
        bus.addListener(PlatformClientHooks::layers);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::tick);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::logout);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::screenInit);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::screenRender);
        NeoForge.EVENT_BUS.addListener(yourscraft.jasdewstarfield.brnquest.client.ClientHealthCommand::register);
    }
    private static void setup(FMLClientSetupEvent event) {
        // Client extensions register during construction; rendering starts only after this freeze.
        ClientTaskPresentationRegistry.freeze();
        ClientRewardPresentationRegistry.freeze();
        ExtensionRegistrationLifecycle.markClientFrozen();
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(ClientKeyRegistry.create()); }
    private static void screenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container) {
            InventoryQuestButton button = new InventoryQuestButton(container);
            event.addListener(button);
            button.place(event.getScreen());
        }
    }
    private static void screenRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        // All init listeners have run by now, regardless of the mods' registration order.
        // Refresh before rendering so drawing, mouse hit testing and keyboard focus share bounds.
        for (var child : event.getScreen().children()) {
            if (child instanceof InventoryQuestButton button) button.place(event.getScreen());
        }
    }
    private static void layers(RegisterGuiLayersEvent event) { event.registerAbove(VanillaGuiLayers.CHAT, QuestHud.LAYER_ID, QuestHud::render); }
    private static void tick(ClientTickEvent.Post event) {
        ClientKeyRegistry.tick();
        // Native configuration pages have their own nested screens; keep the suspended author's
        // lease alive independently of which child is visible, exactly once per client tick.
        ClientEditorState editor = ClientEditorState.get();
        editor.tick();
        editor.pollRenewRequest().ifPresent(request ->
                yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.renewSession(
                        request.sessionId(), request.draftRevision()));
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEditorState.get().disconnected();
        yourscraft.jasdewstarfield.brnquest.client.ClientQuestState.get().disconnected();
    }
}

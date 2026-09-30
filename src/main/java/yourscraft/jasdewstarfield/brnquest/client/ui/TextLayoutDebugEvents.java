package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TextLayoutDebug;

/** Collect a whole screen frame and draw diagnostics above its ordinary panels and hover surfaces. */
@EventBusSubscriber(modid = "brnquest", value = Dist.CLIENT)
public final class TextLayoutDebugEvents {
    private TextLayoutDebugEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void begin(ScreenEvent.Render.Pre event) { TextLayoutDebug.begin(event.getScreen()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finish(ScreenEvent.Render.Post event) {
        TextLayoutDebug.finish(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void tooltip(RenderTooltipEvent.Pre event) {
        // Normal tooltips are replaced only for this mod's active screen; diagnostics allow themselves through.
        if (TextLayoutDebug.active() && !TextLayoutDebug.drawingTooltip()) event.setCanceled(true);
    }
}

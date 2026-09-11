package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTooltipInput;
import java.util.WeakHashMap;

/** Applies one input rule to native item tooltips and custom text without replacing their contents. */
@EventBusSubscriber(modid = "brnquest", value = Dist.CLIENT)
public final class EditorTooltipEvents {
    private static final WeakHashMap<Screen, EditorTooltipInput> INPUT = new WeakHashMap<>();
    private EditorTooltipEvents() {}

    private static EditorTooltipInput state(Screen screen) {
        // Other mods' screens keep their own tooltip policy, including JEI's recipe screen.
        return screen != null && screen.getClass().getPackageName().equals(EditorTooltipEvents.class.getPackageName())
                ? INPUT.computeIfAbsent(screen, ignored -> new EditorTooltipInput()) : null;
    }

    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
        var input = state(event.getScreen());
        if (input != null) input.keyPressed(event.getKeyCode());
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Pre event) {
        var input = state(event.getScreen());
        if (input != null) input.pointer(event.getMouseX(), event.getMouseY(), false);
    }

    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        var input = state(event.getScreen());
        if (input != null) input.pointer(event.getMouseX(), event.getMouseY(), true);
    }

    @SubscribeEvent public static void tooltip(RenderTooltipEvent.Pre event) {
        var input = state(Minecraft.getInstance().screen);
        if (input != null && input.suppressTooltip()) event.setCanceled(true);
    }
}

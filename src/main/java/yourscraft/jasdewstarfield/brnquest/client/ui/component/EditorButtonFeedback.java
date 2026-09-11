package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** One shared activation pulse; never dispatches an action or changes a control's hitbox. */
public final class EditorButtonFeedback {
    private static java.lang.ref.WeakReference<Object> screen = new java.lang.ref.WeakReference<>(null);
    private static UiRect bounds;
    private static long until;
    private EditorButtonFeedback() {}

    public static void pulse(UiRect area) {
        var client = Minecraft.getInstance();
        if (client == null) return;
        screen = new java.lang.ref.WeakReference<>(client.screen); bounds = area; until = System.nanoTime() + 100_000_000L;
    }

    public static boolean pressed(UiRect area) {
        var client = Minecraft.getInstance();
        return client != null && screen.get() == client.screen && area.equals(bounds) && System.nanoTime() < until;
    }

    public static void activate(UiRect area) {
        pulse(area);
        var client = Minecraft.getInstance();
        if (client != null && client.getSoundManager() != null)
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

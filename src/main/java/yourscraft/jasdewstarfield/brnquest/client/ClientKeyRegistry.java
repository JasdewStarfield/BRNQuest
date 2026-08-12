package yourscraft.jasdewstarfield.brnquest.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;

/** Registers the rebindable task-book shortcut used for fast in-world access. */
public final class ClientKeyRegistry {
    private static KeyMapping open;
    private ClientKeyRegistry() {}
    public static KeyMapping create() {
        // J follows the familiar quest/journal convention while remaining rebindable in Controls.
        open = new KeyMapping("key.brnquest.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyMapping.CATEGORY_MISC);
        return open;
    }
    public static void tick() {
        if (open == null || Minecraft.getInstance().player == null) return;
        while (open.consumeClick()) BrnQuestNetwork.requestOpen(ClientQuestState.get().revision());
    }
}

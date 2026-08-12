package yourscraft.jasdewstarfield.brnquest.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;

/** Registers the deliberately unbound-by-default task-book key mapping. */
public final class ClientKeyRegistry {
    private static KeyMapping open;
    private ClientKeyRegistry() {}
    public static KeyMapping create() {
        // The key is intentionally unbound so packs can choose a conflict-free default.
        open = new KeyMapping("key.brnquest.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KeyMapping.CATEGORY_MISC);
        return open;
    }
    public static void tick() {
        if (open == null || Minecraft.getInstance().player == null) return;
        while (open.consumeClick()) BrnQuestNetwork.requestOpen(ClientQuestState.get().revision());
    }
}

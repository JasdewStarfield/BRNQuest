package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Minecraft 1.21.1 scissor coordinates are screen-space, independent of the drawing pose. */
public final class LocalScissor {
    private LocalScissor() {}
    public static void enable(GuiGraphics graphics, UiRect local) {
        UiRect screen = screenBounds(graphics.pose().last().pose(), local);
        graphics.enableScissor(screen.left(), screen.top(), screen.right(), screen.bottom());
    }
    /** Transform all corners so translated or scaled icon slots keep the same visible footprint. */
    static UiRect screenBounds(Matrix4f pose, UiRect local) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int x : new int[]{local.left(), local.right()}) for (int y : new int[]{local.top(), local.bottom()}) {
            var point = pose.transformPosition(new Vector3f(x, y, 0));
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
        }
        return new UiRect((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }
}

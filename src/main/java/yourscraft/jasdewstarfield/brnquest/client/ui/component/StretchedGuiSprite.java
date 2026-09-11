package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;

/** Bounded nine-slice rendering: vanilla 1.21.1 tiles even transparent sprite centers. */
final class StretchedGuiSprite {
    private StretchedGuiSprite() {}

    /** Destination boundaries keep borders fixed; texture boundaries remain in logical sprite pixels. */
    static int[] axis(int length, int leading, int trailing) {
        return new int[]{0, Math.min(leading, length / 2), length - Math.min(trailing, length / 2), length};
    }

    static void render(GuiGraphics graphics, ResourceLocation id, UiRect bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        var sprites = Minecraft.getInstance().getGuiSprites();
        // Resolve on each render so F3+T/resource-pack changes never leave stale atlas coordinates.
        var sprite = sprites.getSprite(id);
        var scaling = sprites.getSpriteScaling(sprite);
        int[] x, y, u, v;
        int width, height;
        if (scaling instanceof GuiSpriteScaling.NineSlice nine) {
            width = nine.width();
            height = nine.height();
            x = axis(bounds.width(), nine.border().left(), nine.border().right());
            y = axis(bounds.height(), nine.border().top(), nine.border().bottom());
            u = new int[]{0, nine.border().left(), width - nine.border().right(), width};
            v = new int[]{0, nine.border().top(), height - nine.border().bottom(), height};
        } else {
            // A replacement without nine-slice metadata still renders as one stretched quad.
            width = height = 1;
            x = new int[]{0, bounds.width()}; y = new int[]{0, bounds.height()};
            u = v = new int[]{0, 1};
        }
        // Flush queued GUI content before immediate drawing to preserve panel/text ordering.
        graphics.flush();
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var pose = graphics.pose().last().pose();
        for (int row = 0; row < y.length - 1; row++) {
            for (int col = 0; col < x.length - 1; col++) {
                if (x[col] == x[col + 1] || y[row] == y[row + 1]) continue;
                float left = bounds.left() + x[col], right = bounds.left() + x[col + 1];
                float top = bounds.top() + y[row], bottom = bounds.top() + y[row + 1];
                float u0 = sprite.getU((float) u[col] / width), u1 = sprite.getU((float) u[col + 1] / width);
                float v0 = sprite.getV((float) v[row] / height), v1 = sprite.getV((float) v[row + 1] / height);
                buffer.addVertex(pose, left, top, 0).setUv(u0, v0).setColor(color);
                buffer.addVertex(pose, left, bottom, 0).setUv(u0, v1).setColor(color);
                buffer.addVertex(pose, right, bottom, 0).setUv(u1, v1).setColor(color);
                buffer.addVertex(pose, right, top, 0).setUv(u1, v0).setColor(color);
            }
        }
        // At most nine quads and one draw per layer, independent of panel area. Color is per vertex.
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }
}

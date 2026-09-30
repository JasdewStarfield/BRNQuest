package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TextLayoutDebug;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Native inputs expose their widget boundary; native button labels expose their padded text slot. */
@Mixin(AbstractWidget.class)
public abstract class TextDebugWidgetMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void brnquest$begin(GuiGraphics graphics, int mouseX, int mouseY, float tick, CallbackInfo callback) {
        if (!TextLayoutDebug.active()) return;
        AbstractWidget widget = (AbstractWidget)(Object)this;
        TextLayoutDebug.pushSlot(graphics, new UiRect(widget.getX(), widget.getY(),
                widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight()), "widget / " + widget.getClass().getSimpleName());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void brnquest$end(GuiGraphics graphics, int mouseX, int mouseY, float tick, CallbackInfo callback) {
        TextLayoutDebug.popSlot();
    }

    @Inject(method = "renderScrollingString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIIII)V", at = @At("HEAD"))
    private static void brnquest$label(GuiGraphics graphics, Font font, Component text, int centerX,
                                       int minX, int minY, int maxX, int maxY, int color, CallbackInfo callback) {
        TextLayoutDebug.pushSlot(graphics, new UiRect(minX, minY, maxX, maxY), "native_button");
        TextLayoutDebug.component(text);
    }

    @Inject(method = "renderScrollingString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIIII)V", at = @At("RETURN"))
    private static void brnquest$labelEnd(GuiGraphics graphics, Font font, Component text, int centerX,
                                          int minX, int minY, int maxX, int maxY, int color, CallbackInfo callback) {
        TextLayoutDebug.popSlot();
    }
}

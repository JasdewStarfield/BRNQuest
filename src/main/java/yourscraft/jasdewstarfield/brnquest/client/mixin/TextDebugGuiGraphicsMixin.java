package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TextLayoutDebug;

/** Observe final text overloads without changing drawing, font styles, or pose transforms. */
@Mixin(GuiGraphics.class)
public abstract class TextDebugGuiGraphicsMixin {
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("HEAD"))
    private void brnquest$string(Font font, String text, float x, float y, int color, boolean shadow,
                                 CallbackInfoReturnable<Integer> callback) {
        if (text != null && TextLayoutDebug.collecting())
            TextLayoutDebug.capture((GuiGraphics)(Object)this, font, text, text, x, y, font.width(text), shadow);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at = @At("HEAD"))
    private void brnquest$sequence(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
                                   CallbackInfoReturnable<Integer> callback) {
        if (TextLayoutDebug.collecting())
            TextLayoutDebug.capture((GuiGraphics)(Object)this, font, text, TextLayoutDebug.plain(text),
                    x, y, font.width(text), shadow);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I", at = @At("HEAD"))
    private void brnquest$component(Font font, Component text, int x, int y, int color, boolean shadow,
                                    CallbackInfoReturnable<Integer> callback) {
        TextLayoutDebug.component(text);
    }

    @Inject(method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"))
    private void brnquest$centeredComponent(Font font, Component text, int x, int y, int color, CallbackInfo callback) {
        TextLayoutDebug.component(text);
    }

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void brnquest$clip(int left, int top, int right, int bottom, CallbackInfo callback) {
        TextLayoutDebug.pushClip(left, top, right, bottom);
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void brnquest$unclip(CallbackInfo callback) { TextLayoutDebug.popClip(); }
}

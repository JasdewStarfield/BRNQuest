package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TextLayoutDebug;
import java.util.List;

/** Retain pre-truncation and wrapping measurements only during a BRNQuest debug frame. */
@Mixin(Font.class)
public abstract class TextDebugFontMixin {
    @Inject(method = "plainSubstrByWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("RETURN"))
    private void brnquest$trim(String text, int width, CallbackInfoReturnable<String> callback) {
        TextLayoutDebug.trimmed((Font)(Object)this, text, width, callback.getReturnValue());
    }

    @Inject(method = "plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("RETURN"))
    private void brnquest$tail(String text, int width, boolean tail, CallbackInfoReturnable<String> callback) {
        TextLayoutDebug.trimmed((Font)(Object)this, text, width, callback.getReturnValue());
    }

    @Inject(method = "split", at = @At("RETURN"))
    private void brnquest$wrap(FormattedText text, int width, CallbackInfoReturnable<List<FormattedCharSequence>> callback) {
        TextLayoutDebug.wrapped((Font)(Object)this, text, width, callback.getReturnValue());
    }
}

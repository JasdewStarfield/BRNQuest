package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RawTextInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RawTextLayout;

/** Chainable input hooks opt in only authored fields; other widgets retain the original operation chain. */
@Mixin(EditBox.class)
public abstract class NativeEditBoxMixin {
    @WrapOperation(method = "insertText", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private String brnquest$filter(String text, Operation<String> original) {
        return (Object) this instanceof RawTextInput input && input.brnquest$rawText()
                ? RawTextLayout.filter(text, false) : original.call(text);
    }
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int brnquest$width(Font font, String text, Operation<Integer> original) {
        return (Object) this instanceof RawTextInput input && input.brnquest$rawText()
                ? RawTextLayout.width(font, text) : original.call(font, text);
    }
}

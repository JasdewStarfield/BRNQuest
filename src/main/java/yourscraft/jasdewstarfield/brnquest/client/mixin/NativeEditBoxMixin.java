package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RawTextInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RawTextLayout;

/** Authored title fields retain raw codes through paste and measure the visible source literally. */
@Mixin(EditBox.class)
public abstract class NativeEditBoxMixin {
    @Redirect(method = "insertText", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private String brnquest$filter(String text) {
        return (Object) this instanceof RawTextInput input && input.brnquest$rawText()
                ? RawTextLayout.filter(text, false) : StringUtil.filterText(text);
    }
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int brnquest$width(Font font, String text) {
        return (Object) this instanceof RawTextInput input && input.brnquest$rawText()
                ? RawTextLayout.width(font, text) : font.width(text);
    }
}

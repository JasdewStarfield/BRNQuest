package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RawTextInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RawTextLayout;

/** Chainable source hooks preserve raw codes in owned buffers and delegate every ordinary buffer. */
@Mixin(MultilineTextField.class)
public abstract class NativeMultilineTextMixin implements RawTextInput {
    @Shadow @Final private Font font;
    @Shadow private String value;
    @Shadow private int cursor;
    @Shadow private int selectCursor;
    @Shadow private int characterLimit;
    @Unique private boolean brnquest$raw;
    @Override public boolean brnquest$rawText() { return brnquest$raw; }
    @Override public void brnquest$rawText(boolean enabled) { brnquest$raw = enabled; }
    @WrapOperation(method = "insertText", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;Z)Ljava/lang/String;"))
    private String brnquest$filter(String text, boolean multiline, Operation<String> original) {
        return brnquest$raw ? RawTextLayout.filter(text, multiline) : original.call(text, multiline);
    }
    @WrapOperation(method = "insertText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/MultilineTextField;truncateInsertionText(Ljava/lang/String;)Ljava/lang/String;"))
    private String brnquest$insertionLimit(MultilineTextField field, String text, Operation<String> original) {
        if (!brnquest$raw) return original.call(field, text);
        // Replacing selected text at the limit still has room for the selected character count.
        int room = Math.max(0, characterLimit - value.length() + Math.abs(cursor - selectCursor));
        int end = Math.min(room, text.length());
        if (end>0 && end<text.length() && Character.isHighSurrogate(text.charAt(end-1))) end--;
        return text.substring(0,end);
    }
    @WrapOperation(method = "seekCursorLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int brnquest$width(Font font, String text, Operation<Integer> original) { return brnquest$raw ? RawTextLayout.width(font, text) : original.call(font, text); }
    @WrapOperation(method = "reflowDisplayLines", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/StringSplitter;splitLines(Ljava/lang/String;ILnet/minecraft/network/chat/Style;ZLnet/minecraft/client/StringSplitter$LinePosConsumer;)V"))
    private void brnquest$lines(StringSplitter splitter, String text, int width, Style style,
                               boolean retainNewline, StringSplitter.LinePosConsumer consumer, Operation<Void> original) {
        if (!brnquest$raw) { original.call(splitter, text, width, style, retainNewline, consumer); return; }
        // Vanilla appends the final empty line itself when the source ends with a newline.
        var lines = RawTextLayout.lines(text, width, value -> RawTextLayout.width(font, value));
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (index == lines.size() - 1 && line.start() == text.length() && text.endsWith("\n")) break;
            consumer.accept(Style.EMPTY, line.start(), line.end());
        }
    }
}

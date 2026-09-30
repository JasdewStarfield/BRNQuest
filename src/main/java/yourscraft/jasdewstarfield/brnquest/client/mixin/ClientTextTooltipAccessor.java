package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the styled visual glyphs so final tooltip wrapping can preserve the original presentation. */
@Mixin(ClientTextTooltip.class)
public interface ClientTextTooltipAccessor {
    @Accessor("text") FormattedCharSequence brnquest$text();
}

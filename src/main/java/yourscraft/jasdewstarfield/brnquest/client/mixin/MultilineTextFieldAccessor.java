package yourscraft.jasdewstarfield.brnquest.client.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only cursor endpoints let toolbar edits wrap the same selection vanilla renders. */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("cursor")
    int brnquest$cursor();

    @Accessor("selectCursor")
    int brnquest$selectCursor();
}

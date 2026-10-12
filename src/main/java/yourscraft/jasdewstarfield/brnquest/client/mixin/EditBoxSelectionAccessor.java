package yourscraft.jasdewstarfield.brnquest.client.mixin;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Restores directional title selections after resizing or returning from a child picker. */
@Mixin(EditBox.class)
public interface EditBoxSelectionAccessor {
    @Accessor("highlightPos") int brnquest$anchor();
}

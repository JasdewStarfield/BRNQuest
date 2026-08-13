package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.Objects;

/** Immutable client-only inputs used to render one task without exposing runtime definitions. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskPresentationContext(Minecraft minecraft, TaskView task, QuestStatus questStatus,
                                      long storedProgress, ItemStack displayedItem) {
    public TaskPresentationContext {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(questStatus, "questStatus");
        displayedItem = displayedItem == null ? ItemStack.EMPTY : displayedItem.copy();
    }
}

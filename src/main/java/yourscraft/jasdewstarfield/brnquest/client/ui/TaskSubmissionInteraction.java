package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;

import java.util.function.Consumer;

/** Client-only factory for a type-owned intent page. The callback sends a request, never proof of completion. */
@FunctionalInterface
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface TaskSubmissionInteraction {
    /** Create a page on the client thread; invoke submit at most once and return to parent on close. */
    Screen createScreen(Screen parent, Consumer<TaskSubmissionSelection> submit);
}

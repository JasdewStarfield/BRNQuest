package legacy;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentation;
import yourscraft.jasdewstarfield.brnquest.client.ui.TaskPresentationContext;

/** Compiled against the older interface, which has no completionDescription method. */
public final class LegacyTaskPresentation implements ClientTaskPresentation {
    public Component objectiveTitle(TaskPresentationContext context) { return Component.literal("Legacy requirement"); }
}

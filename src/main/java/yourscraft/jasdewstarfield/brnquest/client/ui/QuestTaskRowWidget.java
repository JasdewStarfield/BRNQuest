package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

import java.util.Objects;

/** Reusable task-row widget that keeps presentation, geometry and semantic action selection together. */
final class QuestTaskRowWidget {
    enum Action { SUBMIT_TASK, COMPLETE_QUEST, OPEN_TASK_INTERACTION }

    record Model(TaskDefinition task, ClientTaskPresentation presentation,
                 TaskPresentationContext presentationContext, TaskDisplayState displayState,
                 boolean taskSatisfied, boolean questCanSubmit, boolean customSubmissionInteraction) {
        Model {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(presentationContext, "presentationContext");
            Objects.requireNonNull(displayState, "displayState");
        }
    }

    record Layout(int x, int y, int width, UiRect viewport,
                  int mouseX, int mouseY, int attentionPingOffsetY) {}

    record Result(int nextY, UiRect action, UiRect candidates, Action rowAction,
                  RecipeLookupTarget lookup, Component hint, ItemStack hoveredStack) {
        Result {
            hoveredStack = hoveredStack == null ? ItemStack.EMPTY : hoveredStack.copy();
        }
    }

    Result render(GuiGraphics graphics, Font font, Model model, Layout layout) {
        QuestDetailRows.Result row = QuestDetailRows.task(graphics, font, model.task(), model.presentation(),
                model.presentationContext(), model.displayState(), layout.x(), layout.y(), layout.width(),
                layout.viewport(), layout.mouseX(), layout.mouseY(), layout.attentionPingOffsetY());
        ItemStack hoveredStack = row.lookup() == null ? ItemStack.EMPTY : row.lookup().stack();
        return new Result(row.nextY(), row.action(), row.candidates(), action(model),
                row.lookup(), row.hint(), hoveredStack);
    }

    /** Chooses a semantic action only; the screen still revalidates and sends the authoritative request. */
    static Action action(Model model) {
        return action(model.presentation().interactive(model.presentationContext().task()),
                model.taskSatisfied(), model.questCanSubmit(), model.customSubmissionInteraction());
    }

    /** Pure decision boundary used by the client widget and headless regression tests alike. */
    static Action action(boolean interactive, boolean taskSatisfied, boolean questCanSubmit,
                         boolean customSubmissionInteraction) {
        if (!interactive && taskSatisfied && questCanSubmit) {
            return Action.COMPLETE_QUEST;
        }
        return customSubmissionInteraction ? Action.OPEN_TASK_INTERACTION : Action.SUBMIT_TASK;
    }
}

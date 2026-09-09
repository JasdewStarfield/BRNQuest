package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.Objects;

/** Reusable reward-cell widget that owns rendering output without owning reward claiming. */
final class QuestRewardCellWidget {
    record Model(ClientRewardPresentation presentation, RewardPresentationContext presentationContext) {
        Model {
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(presentationContext, "presentationContext");
        }
    }

    record Layout(int x, int y, UiRect viewport, int mouseX, int mouseY, int attentionPingOffsetY) {}

    record Result(UiRect action, UiRect candidates, RecipeLookupTarget lookup, Component hint, ItemStack hoveredStack) {
        Result {
            hoveredStack = hoveredStack == null ? ItemStack.EMPTY : hoveredStack.copy();
        }
    }

    Result render(GuiGraphics graphics, Font font, Model model, Layout layout) {
        QuestDetailRows.Result cell = QuestDetailRows.reward(graphics, font, model.presentation(),
                model.presentationContext(), layout.x(), layout.y(), layout.viewport(),
                layout.mouseX(), layout.mouseY(), layout.attentionPingOffsetY());
        ItemStack hoveredStack = cell.lookup() == null ? ItemStack.EMPTY : cell.lookup().stack();
        return new Result(cell.action(), cell.candidates(), cell.lookup(), cell.hint(), hoveredStack);
    }
}

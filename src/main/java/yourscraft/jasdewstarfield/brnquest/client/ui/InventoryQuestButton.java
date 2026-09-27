package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;

/** Opens the quest book from container screens using the same request as the keyboard shortcut. */
public final class InventoryQuestButton extends Button {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            "brnquest", "textures/gui/icon.png");
    private static final int SIZE = 16;
    private static final int MARGIN = 5;
    private static final int GAP = 4;

    public InventoryQuestButton(AbstractContainerScreen<?> parent) {
        super(MARGIN, MARGIN, SIZE, SIZE, Component.translatable("key.brnquest.open"),
                button -> {
                    // Close the active menu on both sides before requesting a different screen.
                    parent.onClose();
                    BrnQuestNetwork.requestOpen(ClientQuestState.get().revision());
                }, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(getMessage()));
    }

    /** Recheck actual widgets after initialization so BRNTalk's configured position is respected. */
    public void place(Screen screen) {
        for (int y = MARGIN; y + SIZE <= screen.height - MARGIN; y += SIZE + GAP) {
            for (int x = MARGIN; x + SIZE <= screen.width - MARGIN; x += SIZE + GAP) {
                if (hasSpace(screen, x, y)) {
                    setPosition(x, y);
                    visible = true;
                    return;
                }
            }
        }
        // Very small or fully occupied screens retain the keyboard entry point.
        visible = false;
    }

    private boolean hasSpace(Screen screen, int x, int y) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget != this && widget.visible
                    && x < widget.getRight() + GAP && x + SIZE + GAP > widget.getX()
                    && y < widget.getBottom() + GAP && y + SIZE + GAP > widget.getY()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float tint = isHoveredOrFocused() ? 1.0F : 0.8F;
        graphics.setColor(tint, tint, tint, 1.0F);
        graphics.blit(ICON, getX(), getY(), 0, 0, SIZE, SIZE, SIZE, SIZE);
        // The icon shares GuiGraphics state with the inventory and other mods' buttons.
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

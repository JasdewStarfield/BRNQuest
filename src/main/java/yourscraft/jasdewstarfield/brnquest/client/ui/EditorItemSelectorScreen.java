package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorItemSelectorLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.function.Consumer;

/**
 * A client-only ghost inventory: player stacks are copied for authoring metadata and are
 * never moved, split or consumed. The standalone screen is also the future JEI drop target.
 */
public final class EditorItemSelectorScreen extends Screen {
    private final Consumer<ItemStack> selectionConsumer;
    private ItemStack selected = ItemStack.EMPTY;
    private ItemStack carriedGhost = ItemStack.EMPTY;

    public EditorItemSelectorScreen(Consumer<ItemStack> selectionConsumer) {
        super(Component.translatable("screen.brnquest.editor.item_selector.title"));
        this.selectionConsumer = selectionConsumer;
    }

    /** Prevents Screen.render from scheduling a second blur pass above the selector content. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Match QuestScreen's ordering: blur the underlying world/screen once, then draw UI.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x70151820);
        EditorItemSelectorLayout layout = layout();
        UiRect panel = layout.panel();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xF0202632);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 8, 0xFFFFFFFF);
        Component targetLabel = Component.translatable("screen.brnquest.editor.item_selector.target");
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(targetLabel.getString(),
                        layout.targetSlot().left() - panel.left() - 12)),
                panel.left() + 7, panel.top() + 32, 0xFF9FB0C2, false);
        renderSlot(graphics, layout.targetSlot(), selected, mouseX, mouseY, true);
        Component inventoryLabel = Component.translatable("screen.brnquest.editor.item_selector.inventory");
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                        inventoryLabel.getString(), panel.width() - 14)),
                panel.left() + 7, panel.top() + 52, 0xFF9FB0C2, false);

        if (minecraft != null && minecraft.player != null) {
            for (int index = 0; index < 36; index++) {
                renderSlot(graphics, layout.inventorySlot(index),
                        minecraft.player.getInventory().getItem(index), mouseX, mouseY, false);
            }
        }

        EditorButton.render(graphics, font, layout.cancelButton(), Component.translatable("gui.cancel"),
                0xFF343D49, 0xFFFFFFFF, 4);
        EditorButton.render(graphics, font, layout.doneButton(), Component.translatable("gui.done"),
                selected.isEmpty() ? 0xFF343D49 : 0xFF385A72, 0xFFFFFFFF, 4);

        if (!carriedGhost.isEmpty()) graphics.renderItem(carriedGhost, mouseX - 8, mouseY - 8);
        ItemStack hovered = hoveredStack(layout, mouseX, mouseY);
        if (!hovered.isEmpty() && carriedGhost.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSlot(GuiGraphics graphics, UiRect bounds, ItemStack stack,
                            int mouseX, int mouseY, boolean target) {
        int background = bounds.contains(mouseX, mouseY) ? 0xFF69788A : target ? 0xFF59616D : 0xFF3A414B;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), background);
        graphics.fill(bounds.left() + 1, bounds.top() + 1, bounds.right() - 1, bounds.bottom() - 1, 0xFF171B22);
        if (!stack.isEmpty()) graphics.renderItem(stack, bounds.left() + 1, bounds.top() + 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EditorItemSelectorLayout layout = layout();
        if (button == 0 && layout.cancelButton().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (button == 0 && layout.doneButton().contains(mouseX, mouseY)) {
            if (!selected.isEmpty()) {
                selectionConsumer.accept(selected.copyWithCount(1));
                onClose();
            }
            return true;
        }
        if (layout.targetSlot().contains(mouseX, mouseY)) {
            if (button == 1) selected = ItemStack.EMPTY;
            return true;
        }
        int inventoryIndex = layout.inventoryIndexAt(mouseX, mouseY);
        if (button == 0 && inventoryIndex >= 0 && minecraft != null && minecraft.player != null) {
            ItemStack source = minecraft.player.getInventory().getItem(inventoryIndex);
            if (!source.isEmpty()) {
                // Copying on mouse-down gives normal click selection while still showing a drag ghost.
                carriedGhost = source.copyWithCount(1);
                selected = carriedGhost.copy();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !carriedGhost.isEmpty()) {
            if (layout().targetSlot().contains(mouseX, mouseY)) selected = carriedGhost.copyWithCount(1);
            carriedGhost = ItemStack.EMPTY;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        carriedGhost = ItemStack.EMPTY;
        // The selector is a NeoForge GUI layer; the native close path restores QuestScreen.
        super.onClose();
    }

    private ItemStack hoveredStack(EditorItemSelectorLayout layout, int mouseX, int mouseY) {
        if (layout.targetSlot().contains(mouseX, mouseY)) return selected;
        int inventoryIndex = layout.inventoryIndexAt(mouseX, mouseY);
        if (inventoryIndex < 0 || minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        return minecraft.player.getInventory().getItem(inventoryIndex);
    }

    private EditorItemSelectorLayout layout() {
        return new EditorItemSelectorLayout(width, height);
    }
}

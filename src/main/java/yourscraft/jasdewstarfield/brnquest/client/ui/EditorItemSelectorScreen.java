package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorItemSlot;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorItemSelectorLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupSource;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A client-only ghost inventory: player stacks are copied for authoring metadata and are
 * never moved, split or consumed. The standalone screen is also the future JEI drop target.
 */
public final class EditorItemSelectorScreen extends Screen implements RecipeLookupSource {
    /** Tab focuses actions; activation reuses the pointer route and never moves real inventory stacks. */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 258) keyboardSlot = -1;
        if (key >= 262 && key <= 269) {
            int next = keyboardSlot < 0 ? 0 : keyboardSlot;
            next = switch(key) {
                case 262 -> next+1;
                case 263 -> next-1;
                case 264, 267 -> next == 0 ? 1 : next+9;
                case 265, 266 -> next-9;
                case 268 -> 0;
                default -> 36;
            };
            keyboardSlot = Math.max(0,Math.min(36,next)); buttonInput.clearFocus(); return true;
        }
        if ((key == 257 || key == 335 || key == 32) && keyboardSlot >= 0) {
            UiRect area = keyboardSlotBounds();
            mouseClicked(area.centerX(),area.centerY(),0); mouseReleased(area.centerX(),area.centerY(),0); return true;
        }
        if (buttonInput.keyPressed(key, (modifiers & 1) != 0, area -> {
            mouseClicked(area.centerX(), area.centerY(), 0);
            mouseReleased(area.centerX(), area.centerY(), 0);
        })) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    private final EditorButtonInput buttonInput = new EditorButtonInput();
    private int keyboardSlot = -1;

    @Override protected void init() {
        buttonInput.begin(); buttonInput.clearFocus(); keyboardSlot = -1;
    }

    private UiRect keyboardSlotBounds() {
        return keyboardSlot == 0 ? layout().targetSlot()
                : layout().inventorySlot(keyboardSlot < 28 ? keyboardSlot+8 : keyboardSlot-28);
    }
    private final Screen parent;
    private final Consumer<ItemStack> selectionConsumer;
    private ItemStack selected = ItemStack.EMPTY;
    private ItemStack carriedGhost = ItemStack.EMPTY;

    public EditorItemSelectorScreen(Screen parent, Consumer<ItemStack> selectionConsumer) {
        super(Component.translatable("screen.brnquest.editor.item_selector.title"));
        this.parent = parent;
        this.selectionConsumer = selectionConsumer;
    }

    /** Exposes the final screen-space geometry to optional overlay integrations such as JEI. */
    public EditorItemSelectorLayout selectorLayout() {
        return layout();
    }

    /** Accepts an external ghost ingredient without ever mutating the source stack. */
    public void acceptGhostItem(ItemStack stack) {
        selected = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        carriedGhost = ItemStack.EMPTY;
    }

    /** Prevents Screen.render from scheduling a second blur pass above the selector content. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void tick() {
        // The editor is suspended as a parent Screen rather than a NeoForge layer, so keep its
        // lease renewal and response reconciliation active while the selector owns input.
        parent.tick();
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render the persistent editor first so JEI's setScreen recipe round-trip can return to the
        // same visual context. Off-screen mouse coordinates suppress hover and tooltip side effects.
        buttonInput.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        // Blur and dim the completed parent framebuffer before drawing selector content. JEI draws
        // from ScreenEvent.Render.Post afterwards, so its ingredient list remains the final layer.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x70151820);
        EditorItemSelectorLayout layout = layout();
        UiRect panel = layout.panel();
        GraystoneSurface.raised(graphics, panel, 0xFF30332E, true);
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

        buttonInput.render(graphics, font, layout.cancelButton(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttonInput.render(graphics, font, layout.doneButton(),
                EditorButton.Definition.text(Component.translatable("gui.done"), null),
                !selected.isEmpty(), false, EditorButton.Tone.PRIMARY, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (keyboardSlot >= 0) {
            UiRect area = keyboardSlotBounds();
            graphics.renderOutline(area.left(),area.top(),area.width(),area.height(),0xFFE4D29A);
        }
        // Carried ingredients and item Tooltip are the selector's final content layer. JEI's
        // Render.Post overlay intentionally remains above them when the optional mod is present.
        if (!carriedGhost.isEmpty()) graphics.renderItem(carriedGhost, mouseX - 8, mouseY - 8);
        ItemStack hovered = hoveredStack(layout, mouseX, mouseY);
        if (!hovered.isEmpty() && carriedGhost.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void renderSlot(GuiGraphics graphics, UiRect bounds, ItemStack stack,
                            int mouseX, int mouseY, boolean target) {
        EditorItemSlot.render(graphics, bounds, bounds.containsExclusive(mouseX, mouseY), target && !stack.isEmpty());
        if (!stack.isEmpty()) graphics.renderItem(stack, bounds.left() + 1, bounds.top() + 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttonInput.clicked(mouseX, mouseY, button);
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
        if (layout.targetSlot().containsExclusive(mouseX, mouseY)) {
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
            if (layout().targetSlot().containsExclusive(mouseX, mouseY)) selected = carriedGhost.copyWithCount(1);
            carriedGhost = ItemStack.EMPTY;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        carriedGhost = ItemStack.EMPTY;
        if (minecraft != null) {
            // Both the direct selector and a JEI recipe round-trip return to the same editor object.
            minecraft.setScreen(parent);
        }
    }

    private ItemStack hoveredStack(EditorItemSelectorLayout layout, int mouseX, int mouseY) {
        if (layout.targetSlot().containsExclusive(mouseX, mouseY)) return selected;
        int inventoryIndex = layout.inventoryIndexAt(mouseX, mouseY);
        if (inventoryIndex < 0 || minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        return layout.inventorySlot(inventoryIndex).containsExclusive(mouseX, mouseY)
                ? minecraft.player.getInventory().getItem(inventoryIndex) : ItemStack.EMPTY;
    }

    @Override
    public Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY) {
        EditorItemSelectorLayout layout = layout();
        UiRect slot = layout.targetSlot();
        ItemStack stack = selected;
        if (!slot.containsExclusive(mouseX, mouseY)) {
            int inventoryIndex = layout.inventoryIndexAt(mouseX, mouseY);
            if (inventoryIndex < 0 || minecraft == null || minecraft.player == null) return Optional.empty();
            slot = layout.inventorySlot(inventoryIndex);
            stack = minecraft.player.getInventory().getItem(inventoryIndex);
        }
        if (stack.isEmpty()) return Optional.empty();
        // Share the whole slot with hover and native Tooltip; the exclusive edge belongs to the next slot.
        RecipeLookupTarget target = new RecipeLookupTarget(stack, slot);
        return target.contains(mouseX, mouseY) ? Optional.of(target) : Optional.empty();
    }

    private EditorItemSelectorLayout layout() {
        return new EditorItemSelectorLayout(width, height);
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorItemSelectorLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupSource;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gameplay inventory picker for consume objectives. The client only sends ordered slot indices;
 * the server rebuilds the plan from live stacks before it removes anything.
 */
public final class ItemSubmissionScreen extends Screen implements RecipeLookupSource {
    private final Screen parent;
    private final ItemChoiceMatcher.Spec spec;
    private final Consumer<List<Integer>> submissionConsumer;
    private final LinkedHashSet<Integer> selectedSlots = new LinkedHashSet<>();

    public ItemSubmissionScreen(Screen parent, ItemChoiceMatcher.Spec spec,
                                Consumer<List<Integer>> submissionConsumer) {
        super(Component.translatable("screen.brnquest.item_submission.title"));
        this.parent = parent;
        this.spec = spec;
        this.submissionConsumer = submissionConsumer;
    }

    @Override
    public void tick() {
        parent.tick();
        super.tick();
    }

    /** Prevents Screen.render from scheduling another blur pass above this inventory panel. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x70151820);

        EditorItemSelectorLayout layout = layout();
        UiRect panel = layout.panel();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xF0202632);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 8, 0xFFFFFFFF);

        ItemChoiceMatcher.MatchPlan plan = currentPlan();
        boolean canSubmit = !selectedSlots.isEmpty() && plan.selectionValid();
        Component instruction = Component.translatable("screen.brnquest.item_submission.instruction",
                spec.requiredEntries(), spec.entries().size());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                        instruction.getString(), layout.targetSlot().left() - panel.left() - 12)),
                panel.left() + 7, panel.top() + 31, 0xFF9FB0C2, false);
        renderTarget(graphics, layout.targetSlot());

        Component summary = selectedSlots.isEmpty()
                ? Component.translatable("screen.brnquest.item_submission.select_hint")
                : Component.translatable(canSubmit
                        ? "screen.brnquest.item_submission.selected_ready"
                        : "screen.brnquest.item_submission.selected_invalid", selectedSlots.size());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(summary.getString(), panel.width() - 14)),
                panel.left() + 7, panel.top() + 50,
                canSubmit ? 0xFF80D49B : selectedSlots.isEmpty() ? 0xFF9FB0C2 : 0xFFFFC16A, false);

        if (minecraft != null && minecraft.player != null) {
            for (int slotIndex = 0; slotIndex < 36; slotIndex++) {
                renderInventorySlot(graphics, layout.inventorySlot(slotIndex), slotIndex,
                        minecraft.player.getInventory().getItem(slotIndex), mouseX, mouseY);
            }
        }

        EditorButton.renderInteractive(graphics, font, layout.cancelButton(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        EditorButton.renderInteractive(graphics, font, layout.doneButton(),
                EditorButton.Definition.text(Component.translatable("screen.brnquest.item_choice.submit"), null),
                canSubmit, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack hovered = hoveredStack(layout, mouseX, mouseY);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void renderTarget(GuiGraphics graphics, UiRect bounds) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFF59616D);
        graphics.fill(bounds.left() + 1, bounds.top() + 1, bounds.right() - 1, bounds.bottom() - 1, 0xFF171B22);
        if (minecraft == null || minecraft.level == null) return;
        List<ItemStack> accepted = ItemChoiceMatcher.displayedCandidates(minecraft.level.registryAccess(), spec);
        if (!accepted.isEmpty()) graphics.renderItem(accepted.getFirst(), bounds.left() + 1, bounds.top() + 1);
    }

    private void renderInventorySlot(GuiGraphics graphics, UiRect bounds, int slotIndex, ItemStack stack,
                                     int mouseX, int mouseY) {
        boolean accepted = accepts(stack);
        boolean selected = selectedSlots.contains(slotIndex);
        int border = selected ? 0xFF79B7E5 : accepted && bounds.contains(mouseX, mouseY)
                ? 0xFF69788A : 0xFF3A414B;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), border);
        graphics.fill(bounds.left() + 1, bounds.top() + 1, bounds.right() - 1, bounds.bottom() - 1, 0xFF171B22);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, bounds.left() + 1, bounds.top() + 1);
            graphics.renderItemDecorations(font, stack, bounds.left() + 1, bounds.top() + 1);
            if (!accepted) {
                // Item rendering uses its own positive GUI depth. Lift the disabled mask above that
                // depth so the stack itself, not merely its slot background, is visibly dimmed.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 250);
                graphics.fill(bounds.left() + 1, bounds.top() + 1, bounds.right() - 1, bounds.bottom() - 1,
                        0xA0000000);
                graphics.pose().popPose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EditorItemSelectorLayout layout = layout();
        if (button == 0 && layout.cancelButton().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (button == 0 && layout.doneButton().contains(mouseX, mouseY)
                && !selectedSlots.isEmpty() && currentPlan().selectionValid()) {
            submissionConsumer.accept(List.copyOf(selectedSlots));
            onClose();
            return true;
        }
        int slot = layout.inventoryIndexAt(mouseX, mouseY);
        if (button == 0 && slot >= 0 && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (accepts(stack)) {
                if (!selectedSlots.remove(slot)) selectedSlots.add(slot);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private boolean accepts(ItemStack stack) {
        return minecraft != null && minecraft.level != null
                && ItemChoiceMatcher.accepts(minecraft.level.registryAccess(), spec, stack);
    }

    private ItemChoiceMatcher.MatchPlan currentPlan() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return ItemChoiceMatcher.plan(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY), List.of(), spec, List.of());
        }
        return ItemChoiceMatcher.plan(minecraft.level.registryAccess(),
                minecraft.player.getInventory().items, spec, List.copyOf(selectedSlots));
    }

    private ItemStack hoveredStack(EditorItemSelectorLayout layout, double mouseX, double mouseY) {
        int slot = layout.inventoryIndexAt(mouseX, mouseY);
        if (slot < 0 || minecraft == null || minecraft.player == null
                || !itemBounds(layout.inventorySlot(slot)).contains(mouseX, mouseY)) return ItemStack.EMPTY;
        return minecraft.player.getInventory().getItem(slot);
    }

    @Override
    public Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY) {
        EditorItemSelectorLayout layout = layout();
        int slot = layout.inventoryIndexAt(mouseX, mouseY);
        if (slot < 0 || minecraft == null || minecraft.player == null) return Optional.empty();
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        RecipeLookupTarget target = new RecipeLookupTarget(stack, itemBounds(layout.inventorySlot(slot)));
        return !stack.isEmpty() && target.contains(mouseX, mouseY) ? Optional.of(target) : Optional.empty();
    }

    private EditorItemSelectorLayout layout() {
        return new EditorItemSelectorLayout(width, height);
    }

    /** Shares the exact panel geometry with JEI without exposing submission state. */
    public EditorItemSelectorLayout selectorLayout() {
        return layout();
    }

    private static UiRect itemBounds(UiRect slot) {
        return new UiRect(slot.left() + 1, slot.top() + 1, slot.right() - 1, slot.bottom() - 1);
    }
}

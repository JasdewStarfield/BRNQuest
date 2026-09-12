package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonWidget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorItemSlot;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
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
    private final String sourceRevision = yourscraft.jasdewstarfield.brnquest.client.ClientQuestState.get().revision();
    private EditorButtonWidget submit;
    private boolean sent;
    private boolean inventoryFocused;
    private int focusedInventoryIndex = 9;

    @Override protected void init() {
        var layout = layout();
        var cancel = layout.cancelButton();
        var done = layout.doneButton();
        addRenderableWidget(new EditorButtonWidget(cancel.left(), cancel.top(), cancel.width(), cancel.height(),
                Component.translatable("gui.cancel"), button -> onClose()));
        submit = addRenderableWidget(new EditorButtonWidget(done.left(), done.top(), done.width(), done.height(),
                Component.translatable("screen.brnquest.item_choice.submit"), button -> {
            // Revalidate live inventory at activation; resizing or a second click cannot resend this selection.
            if (!canSubmit()) return;
            sent = true;
            submissionConsumer.accept(List.copyOf(selectedSlots));
            onClose();
        }));
        submit.active = canSubmit();
    }

    private boolean canSubmit() {
        return canSubmit(currentPlan());
    }

    private boolean canSubmit(ItemChoiceMatcher.MatchPlan plan) {
        return !sent && sourceRevision.equals(yourscraft.jasdewstarfield.brnquest.client.ClientQuestState.get().revision())
                && !selectedSlots.isEmpty() && plan.selectionValid();
    }

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
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);

        EditorItemSelectorLayout layout = layout();
        UiRect panel = layout.panel();
        GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 8, 0xFFFFFFFF);

        ItemChoiceMatcher.MatchPlan plan = currentPlan();
        boolean canSubmit = canSubmit(plan);
        submit.active = canSubmit;
        Component instruction = Component.translatable("screen.brnquest.item_submission.instruction",
                spec.requiredEntries(), spec.entries().size());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                        instruction.getString(), layout.targetSlot().left() - panel.left() - 12)),
                panel.left() + 7, panel.top() + 31, GraystonePalette.SECONDARY, false);
        renderTarget(graphics, layout.targetSlot(), plan.representative());

        Component summary = selectedSlots.isEmpty()
                ? Component.translatable("screen.brnquest.item_submission.select_hint")
                : Component.translatable(canSubmit
                        ? "screen.brnquest.item_submission.selected_ready"
                        : "screen.brnquest.item_submission.selected_invalid", selectedSlots.size());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(summary.getString(), panel.width() - 14)),
                panel.left() + 7, panel.top() + 50,
                canSubmit ? 0xFF80D49B : selectedSlots.isEmpty() ? GraystonePalette.SECONDARY : 0xFFFFC16A, false);

        if (minecraft != null && minecraft.player != null) {
            for (int slotIndex = 0; slotIndex < 36; slotIndex++) {
                renderInventorySlot(graphics, layout.inventorySlot(slotIndex), slotIndex,
                        minecraft.player.getInventory().getItem(slotIndex), mouseX, mouseY);
            }
        }

        // Show the computed removal count, not the sum of the selected inventory stacks.
        int removalCount = plan.candidates().stream().filter(ItemChoiceMatcher.Candidate::selected)
                .flatMap(candidate -> candidate.removals().stream()).mapToInt(ItemChoiceMatcher.Removal::count).sum();
        Component amount = Component.translatable("screen.brnquest.item_submission.planned", canSubmit ? removalCount : 0);
        yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextRenderer.drawFittedString(
                graphics, font, amount, panel.left() + 7, panel.top() + 20, panel.width() - 14, 0xFFB7C5A7, 0.75F);
        if (inventoryFocused) {
            var focused = layout.inventorySlot(focusedInventoryIndex);
            graphics.renderOutline(focused.left(), focused.top(), focused.width(), focused.height(), GraystonePalette.ACCENT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack hovered = hoveredStack(layout, mouseX, mouseY);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void renderTarget(GuiGraphics graphics, UiRect bounds, ItemStack representative) {
        EditorItemSlot.render(graphics, bounds, false, false);
        if (minecraft == null || minecraft.level == null) return;
        if (!representative.isEmpty()) graphics.renderItem(representative, bounds.left() + 1, bounds.top() + 1);
    }

    private void renderInventorySlot(GuiGraphics graphics, UiRect bounds, int slotIndex, ItemStack stack,
                                     int mouseX, int mouseY) {
        boolean accepted = accepts(stack);
        boolean selected = selectedSlots.contains(slotIndex);
        EditorItemSlot.render(graphics, bounds, accepted && bounds.containsExclusive(mouseX, mouseY), selected);
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
        inventoryFocused = false;
        EditorItemSelectorLayout layout = layout();
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

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // F6 moves between native footer buttons and the vanilla-ordered inventory grid.
        if (key == 295) { inventoryFocused = !inventoryFocused; if (inventoryFocused) setFocused(null); return true; }
        if (key == 258) inventoryFocused = false;
        if (inventoryFocused) {
            int visual = focusedInventoryIndex < 9 ? focusedInventoryIndex + 27 : focusedInventoryIndex - 9;
            int step = switch (key) { case 262 -> 1; case 263 -> -1; case 264 -> 9; case 265 -> -9; default -> 0; };
            if (step != 0) {
                visual = Math.max(0, Math.min(35, visual + step));
                focusedInventoryIndex = visual >= 27 ? visual - 27 : visual + 9;
                return true;
            }
            if (key == 257 || key == 335 || key == 32) {
                if (minecraft != null && minecraft.player != null && accepts(minecraft.player.getInventory().getItem(focusedInventoryIndex))) {
                    if (!selectedSlots.remove(focusedInventoryIndex)) selectedSlots.add(focusedInventoryIndex);
                }
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
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
        if (layout.targetSlot().containsExclusive(mouseX, mouseY)) return currentPlan().representative();
        int slot = layout.inventoryIndexAt(mouseX, mouseY);
        if (slot < 0 || minecraft == null || minecraft.player == null
                || !itemBounds(layout.inventorySlot(slot)).containsExclusive(mouseX, mouseY)) return ItemStack.EMPTY;
        return minecraft.player.getInventory().getItem(slot);
    }

    @Override
    public Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY) {
        EditorItemSelectorLayout layout = layout();
        if (layout.targetSlot().containsExclusive(mouseX, mouseY))
            return recipeLookupTargetAt(currentPlan().representative(), layout.targetSlot(), mouseX, mouseY);
        int slot = layout.inventoryIndexAt(mouseX, mouseY);
        if (slot < 0 || minecraft == null || minecraft.player == null) return Optional.empty();
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return recipeLookupTargetAt(stack, itemBounds(layout.inventorySlot(slot)), mouseX, mouseY);
    }

    /** JEI queries every clicked slot before the Screen handles it, including empty inventory slots. */
    static Optional<RecipeLookupTarget> recipeLookupTargetAt(ItemStack stack, UiRect bounds,
                                                              double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty() || bounds == null || !bounds.containsExclusive(mouseX, mouseY)) {
            return Optional.empty();
        }
        return Optional.of(new RecipeLookupTarget(stack, bounds));
    }

    private EditorItemSelectorLayout layout() {
        return new EditorItemSelectorLayout(width, height);
    }

    /** Shares the exact panel geometry with JEI without exposing submission state. */
    public EditorItemSelectorLayout selectorLayout() {
        return layout();
    }

    private static UiRect itemBounds(UiRect slot) {
        return slot;
    }
}

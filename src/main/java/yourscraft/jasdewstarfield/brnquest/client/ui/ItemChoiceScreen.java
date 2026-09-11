package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorItemSlot;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupSource;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shared candidate Screen: gameplay opens a read-only grid, while authoring enables
 * ghost additions, removal, ordering and tag/list controls without moving real items.
 */
public final class ItemChoiceScreen extends Screen implements RecipeLookupSource {
    private static final int PANEL_WIDTH = 220;
    private static final int EDIT_PANEL_HEIGHT = 254;
    private static final int VIEW_PANEL_HEIGHT = 142;
    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;

    /** Tab focuses actions; activation reuses the pointer route and never moves real inventory stacks. */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // F6 switches the two item regions; Tab remains dedicated to fields and actions.
        if (key == 295 && editing) {
            setFocused(null); buttonInput.clearFocus();
            inventoryKeyboard = inventoryKeyboard < 0 ? 0 : -1;
            candidateKeyboard = inventoryKeyboard < 0 && !candidates().isEmpty() ? 0 : -1;
            return true;
        }
        if (key == 258) inventoryKeyboard = -1;
        if (inventoryKeyboard >= 0 && key >= 262 && key <= 269) {
            int next = switch(key) {
                case 262 -> inventoryKeyboard+(inventoryKeyboard%9<8?1:0);
                case 263 -> inventoryKeyboard-(inventoryKeyboard%9>0?1:0);
                case 264, 267 -> inventoryKeyboard+9;
                case 265, 266 -> inventoryKeyboard-9;
                case 268 -> 0;
                default -> 35;
            };
            inventoryKeyboard = Math.max(0,Math.min(35,next)); return true;
        }
        if (inventoryKeyboard >= 0 && (key == 257 || key == 335 || key == 32)) {
            UiRect area = inventorySlot(inventoryKeyboard < 27 ? inventoryKeyboard+9 : inventoryKeyboard-27);
            mouseClicked(area.centerX(),area.centerY(),0); mouseReleased(area.centerX(),area.centerY(),0); return true;
        }
        if (key == 258 && tagMode && editing && tagField != null && !tagField.isFocused()
                && buttonInput.atBoundary((modifiers & 1) != 0)) {
            buttonInput.clearFocus(); candidateKeyboard = -1; setFocused(tagField); return true;
        }
        if (tagField != null && tagField.isFocused() && key != 258) return super.keyPressed(key, scan, modifiers);
        if (key == 258) { setFocused(null); candidateKeyboard = -1; }
        int count = candidates().size();
        if (count > 0 && key >= 262 && key <= 269) {
            int next = candidateKeyboard < 0 ? 0 : candidateKeyboard;
            next = switch (key) {
                case 262 -> next + (next % COLUMNS < COLUMNS-1 ? 1 : 0);
                case 263 -> next - (next % COLUMNS > 0 ? 1 : 0);
                case 264 -> candidateKeyboard < 0 ? 0 : next+COLUMNS;
                case 265 -> candidateKeyboard < 0 ? 0 : next-COLUMNS;
                case 266 -> next-COLUMNS*Math.max(1,candidateViewport().height()/SLOT_SIZE);
                case 267 -> next+COLUMNS*Math.max(1,candidateViewport().height()/SLOT_SIZE);
                case 268 -> 0;
                default -> count-1;
            };
            candidateKeyboard = Math.max(0,Math.min(count-1,next)); buttonInput.clearFocus();
            int top = candidateKeyboard/COLUMNS*SLOT_SIZE;
            if (top < renderedScroll) candidateScroll.snap(top);
            else if (top+SLOT_SIZE > renderedScroll+candidateViewport().height())
                candidateScroll.snap(Math.max(0,top+SLOT_SIZE-candidateViewport().height()));
            return true;
        }
        if ((key == 257 || key == 335) && candidateKeyboard >= 0 && candidateKeyboard < count) {
            if (editing && !tagMode) selectedIndex = candidateKeyboard;
            return true;
        }
        if (buttonInput.keyPressed(key, (modifiers & 1) != 0, area -> {
            mouseClicked(area.centerX(), area.centerY(), 0);
            mouseReleased(area.centerX(), area.centerY(), 0);
        })) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    private final EditorButtonInput buttonInput = new EditorButtonInput();
    private int candidateKeyboard = -1;
    private int inventoryKeyboard = -1;
    private final Screen parent;
    private final boolean editing;
    private final Consumer<ItemChoiceMatcher.Spec> resultConsumer;
    private final List<ItemStack> listCandidates = new ArrayList<>();
    private final List<Integer> listCounts = new ArrayList<>();
    private final EditorSmoothScroll candidateScroll = new EditorSmoothScroll();
    private ItemChoiceMatcher.Spec original;
    private boolean tagMode;
    private int targetRequiredEntries = 1;
    private int tagRequiredCount = 1;
    private int selectedIndex = -1;
    private EditBox tagField;
    private String tagValue = "";
    private boolean candidatesInitialized;
    private Component message;
    private long previousFrameNanos;
    private double renderedScroll;

    public ItemChoiceScreen(Screen parent, ItemChoiceMatcher.Spec initial,
                            boolean editing, Consumer<ItemChoiceMatcher.Spec> resultConsumer) {
        super(Component.translatable(editing
                ? "screen.brnquest.item_choice.edit_title"
                : "screen.brnquest.item_choice.view_title"));
        this.parent = parent;
        this.editing = editing;
        this.resultConsumer = resultConsumer;
        this.original = initial;
        if (initial != null && initial.entries().getFirst().kind() == ItemChoiceMatcher.EntryKind.TAG) {
            tagMode = true;
            tagValue = initial.entries().getFirst().value();
            tagRequiredCount = initial.entries().getFirst().requiredCount();
        } else if (initial != null) {
            targetRequiredEntries = initial.requiredEntries();
        }
    }

    /** Used when adding a new task before a valid non-empty matcher exists. */
    public static ItemChoiceScreen createEditor(Screen parent, Consumer<ItemChoiceMatcher.Spec> resultConsumer) {
        return new ItemChoiceScreen(parent, null, true, resultConsumer);
    }

    @Override
    protected void init() {
        buttonInput.begin(); buttonInput.clearFocus();
        candidateKeyboard = -1; inventoryKeyboard = -1;
        if (tagField != null) tagValue = tagField.getValue();
        tagField = new EditBox(font, 0, 0, 10, 18,
                Component.translatable("screen.brnquest.item_choice.tag"));
        tagField.setMaxLength(256);
        tagField.setValue(tagValue);
        // Returning from the tag picker rebuilds this widget. Reset the new EditBox's
        // horizontal scroll immediately so the selected tag is visible before it gains focus.
        tagField.setCursorPosition(0);
        tagField.setTextColor(0xFFFFFFFF);
        addRenderableWidget(tagField);
        if (!candidatesInitialized && minecraft != null && minecraft.level != null
                && original != null && original.entries().getFirst().kind() == ItemChoiceMatcher.EntryKind.ITEM) {
            listCandidates.addAll(ItemChoiceMatcher.displayedCandidates(minecraft.level.registryAccess(), original));
            original.entries().forEach(entry -> listCounts.add(entry.requiredCount()));
            candidatesInitialized = true;
        }
        updateTagFieldGeometry();
    }

    @Override
    public void tick() {
        parent.tick();
        super.tick();
    }

    /** Prevents Screen.render from blurring above the completed candidate panel. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttonInput.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x70151820);
        UiRect panel = panelBounds();
        // Keep the native item slots, ingredient hitboxes and parent lifecycle inside the shared skin.
        GraystoneSurface.raised(graphics, panel, 0xFF30332E, true);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 7, 0xFFFFFFFF);

        if (editing) renderEditorChrome(graphics, panel, mouseX, mouseY);
        renderCandidates(graphics, mouseX, mouseY);
        if (editing) renderInventory(graphics, panel, mouseX, mouseY);

        renderButtons(graphics, panel, mouseX, mouseY);
        updateTagFieldGeometry();
        super.render(graphics, mouseX, mouseY, partialTick);

        // Native Tooltip and optional recipe hints must resolve the exact same clipped slot.
        recipeLookupTargetAt(mouseX, mouseY).ifPresent(target -> {
            // Lookup ingredients normalize count to one; native tooltips retain the original stack for other mods.
            ItemStack hovered = candidateAt(mouseX, mouseY).map(index -> candidates().get(index))
                    .orElseGet(() -> inventoryStackAt(mouseX, mouseY));
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        });
    }
    private void renderEditorChrome(GuiGraphics graphics, UiRect panel, int mouseX, int mouseY) {
        buttonInput.render(graphics, font, modeListBounds(),
                EditorButton.Definition.text(Component.translatable("screen.brnquest.item_choice.mode.list"), null),
                true, !tagMode, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttonInput.render(graphics, font, modeTagBounds(),
                EditorButton.Definition.text(Component.translatable("screen.brnquest.item_choice.mode.tag"), null),
                true, tagMode, EditorButton.Tone.NEUTRAL, mouseX, mouseY);

        if (tagMode) {
            graphics.drawString(font, Component.translatable("screen.brnquest.item_choice.tag"),
                    panel.left() + 7, panel.top() + 49, 0xFF9FB0C2, false);
            graphics.drawString(font, Component.literal("×" + tagRequiredCount),
                    panel.right() - 104, panel.top() + 49, 0xFF9FB0C2, false);
            renderSmallButton(graphics, decrementBounds(), "−", tagRequiredCount > 1, mouseX, mouseY);
            renderSmallButton(graphics, incrementBounds(), "+", tagRequiredCount < Integer.MAX_VALUE, mouseX, mouseY);
        } else {
            int selectedCount = selectedIndex >= 0 && selectedIndex < listCounts.size()
                    ? listCounts.get(selectedIndex) : 1;
            Component requirement = Component.translatable("screen.brnquest.item_choice.entry_count", selectedCount);
            int requirementWidth = Math.max(1, decrementBounds().left() - panel.left() - 11);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                    requirement.getString(), requirementWidth)), panel.left() + 7, panel.top() + 49,
                    0xFF9FB0C2, false);
            renderSmallButton(graphics, decrementBounds(), "−", selectedIndex >= 0 && selectedCount > 1,
                    mouseX, mouseY);
            renderSmallButton(graphics, incrementBounds(), "+",
                    selectedIndex >= 0 && selectedCount < Integer.MAX_VALUE, mouseX, mouseY);
            renderSmallButton(graphics, moveLeftBounds(), "←", selectedIndex > 0, mouseX, mouseY);
            renderSmallButton(graphics, moveRightBounds(), "→",
                    selectedIndex >= 0 && selectedIndex + 1 < listCandidates.size(), mouseX, mouseY);
            renderSmallButton(graphics, removeBounds(), "×", selectedIndex >= 0, mouseX, mouseY);
        }
    }

    private void renderCandidates(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ItemStack> candidates = candidates();
        UiRect viewport = candidateViewport();
        int rows = (candidates.size() + COLUMNS - 1) / COLUMNS;
        int contentHeight = rows * SLOT_SIZE;
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0 ? 1.0 / 60.0
                : Math.min(0.1, Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0));
        previousFrameNanos = now;
        renderedScroll = candidateScroll.frameAndRender(graphics, viewport.right() + 2,
                viewport.top(), viewport.bottom(), contentHeight, viewport.height(), elapsed,
                BrnQuestClientConfig.VALUES.smoothSpeed.get());
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        for (int index = 0; index < candidates.size(); index++) {
            UiRect slot = candidateSlot(index);
            if (slot.bottom() <= viewport.top() || slot.top() >= viewport.bottom()) continue;
            boolean selected = editing && !tagMode && index == selectedIndex;
            EditorItemSlot.render(graphics, slot, slot.intersection(viewport).containsExclusive(mouseX, mouseY), selected);
            graphics.renderItem(candidates.get(index), slot.left() + 1, slot.top() + 1);
            if (index == candidateKeyboard) graphics.renderOutline(slot.left(),slot.top(),slot.width(),slot.height(),0xFFE4D29A);
            int required = displayedRequiredCount(index);
            if (required > 1) {
                ItemStack decoration = candidates.get(index).copyWithCount(required);
                graphics.renderItemDecorations(font, decoration, slot.left() + 1, slot.top() + 1);
            }
        }
        graphics.disableScissor();
        Component summary = message != null ? message : candidates.isEmpty()
                ? Component.translatable("screen.brnquest.item_choice.empty")
                : Component.translatable("screen.brnquest.item_choice.accepted_count", candidates.size());
        int summaryColor = message != null || candidates.isEmpty() ? 0xFFFFA070 : 0xFF9FB0C2;
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(summary.getString(), viewport.width())),
                viewport.left(), viewport.bottom() + 3, summaryColor, false);
    }

    private void renderInventory(GuiGraphics graphics, UiRect panel, int mouseX, int mouseY) {
        Component hint = Component.translatable(tagMode
                ? "screen.brnquest.item_choice.inventory_tag_hint"
                : "screen.brnquest.item_choice.inventory_add_hint");
        int inventoryLeft = panel.centerX() - COLUMNS * SLOT_SIZE / 2;
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                        hint.getString(), COLUMNS * SLOT_SIZE)),
                inventoryLeft, inventoryTop() - 11, 0xFF9FB0C2, false);
        if (minecraft == null || minecraft.player == null) return;
        for (int index = 0; index < 36; index++) {
            UiRect slot = inventorySlot(index);
            ItemStack stack = minecraft.player.getInventory().getItem(index);
            EditorItemSlot.render(graphics, slot, slot.containsExclusive(mouseX, mouseY), false);
            if (!stack.isEmpty()) graphics.renderItem(stack, slot.left() + 1, slot.top() + 1);
            if (inventoryKeyboard >= 0 && index == (inventoryKeyboard < 27 ? inventoryKeyboard+9 : inventoryKeyboard-27))
                graphics.renderOutline(slot.left(),slot.top(),slot.width(),slot.height(),0xFFE4D29A);
        }
    }

    private void renderButtons(GuiGraphics graphics, UiRect panel, int mouseX, int mouseY) {
        if (editing) {
            buttonInput.render(graphics, font, cancelBounds(),
                    EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                    true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            buttonInput.render(graphics, font, doneBounds(),
                    EditorButton.Definition.text(Component.translatable("gui.done"), null),
                    canFinish(), false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        } else {
            buttonInput.render(graphics, font, closeBounds(),
                    EditorButton.Definition.text(Component.translatable("gui.done"), null),
                    true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }
    }

    private void renderSmallButton(GuiGraphics graphics, UiRect bounds, String glyph,
                                   boolean enabled, int mouseX, int mouseY) {
        buttonInput.render(graphics, font, bounds,
                EditorButton.Definition.text(Component.literal(glyph), null),
                enabled, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttonInput.clicked(mouseX, mouseY, button);
        if (!editing && closeBounds().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (editing && cancelBounds().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (editing && doneBounds().contains(mouseX, mouseY)) {
            finishEditing();
            return true;
        }
        if (editing && modeListBounds().contains(mouseX, mouseY)) {
            tagMode = false;
            candidateScroll.snap(0);
            message = null;
            return true;
        }
        if (editing && modeTagBounds().contains(mouseX, mouseY)) {
            tagMode = true;
            selectedIndex = -1;
            candidateScroll.snap(0);
            message = null;
            return true;
        }
        if (editing && handleControlClick(mouseX, mouseY)) return true;
        Optional<Integer> candidate = candidateAt(mouseX, mouseY);
        if (editing && !tagMode && candidate.isPresent()) {
            selectedIndex = candidate.get();
            if (button == 1) removeSelected();
            return true;
        }
        if (editing && button == 0) {
            ItemStack stack = inventoryStackAt(mouseX, mouseY);
            if (!stack.isEmpty()) {
                acceptGhostItem(stack);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleControlClick(double mouseX, double mouseY) {
        if (decrementBounds().contains(mouseX, mouseY) && tagMode && tagRequiredCount > 1) {
            tagRequiredCount--;
            return true;
        }
        if (incrementBounds().contains(mouseX, mouseY) && tagMode && tagRequiredCount < Integer.MAX_VALUE) {
            tagRequiredCount++;
            return true;
        }
        if (decrementBounds().contains(mouseX, mouseY) && !tagMode && selectedIndex >= 0
                && listCounts.get(selectedIndex) > 1) {
            listCounts.set(selectedIndex, listCounts.get(selectedIndex) - 1);
            return true;
        }
        if (incrementBounds().contains(mouseX, mouseY) && !tagMode && selectedIndex >= 0
                && listCounts.get(selectedIndex) < Integer.MAX_VALUE) {
            listCounts.set(selectedIndex, listCounts.get(selectedIndex) + 1);
            return true;
        }
        if (moveLeftBounds().contains(mouseX, mouseY) && selectedIndex > 0) {
            java.util.Collections.swap(listCandidates, selectedIndex, selectedIndex - 1);
            java.util.Collections.swap(listCounts, selectedIndex, selectedIndex - 1);
            selectedIndex--;
            return true;
        }
        if (moveRightBounds().contains(mouseX, mouseY) && selectedIndex >= 0
                && selectedIndex + 1 < listCandidates.size()) {
            java.util.Collections.swap(listCandidates, selectedIndex, selectedIndex + 1);
            java.util.Collections.swap(listCounts, selectedIndex, selectedIndex + 1);
            selectedIndex++;
            return true;
        }
        if (removeBounds().contains(mouseX, mouseY) && selectedIndex >= 0) {
            removeSelected();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect viewport = candidateViewport();
        if (viewport.contains(mouseX, mouseY)) {
            int rows = (candidates().size() + COLUMNS - 1) / COLUMNS;
            candidateScroll.scrollWheel(scrollY, BrnQuestClientConfig.VALUES.scrollStep.get(),
                    rows * SLOT_SIZE, viewport.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** Optional JEI calls this method; the copied ingredient never mutates its source. */
    public void acceptGhostItem(ItemStack stack) {
        if (!editing || stack == null || stack.isEmpty()) return;
        if (tagMode) {
            List<ResourceLocation> tags = stack.getTags().map(key -> key.location())
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            if (tags.isEmpty()) {
                message = Component.translatable("screen.brnquest.item_choice.no_item_tag");
                return;
            }
            if (tags.size() > 1 && minecraft != null) {
                // Do not silently choose the alphabetically first tag: similarly named forge/common
                // tags can have materially different matching scope for the resulting objective.
                minecraft.setScreen(new TagChoiceScreen(this, stack, tags, selectedTag -> {
                    tagValue = selectedTag.toString();
                    tagField.setValue(tagValue);
                    message = null;
                    candidateScroll.snap(0);
                }));
                return;
            }
            tagValue = tags.getFirst().toString();
            tagField.setValue(tagValue);
            message = null;
            candidateScroll.snap(0);
            return;
        }
        if (listCandidates.size() >= ItemChoiceMatcher.MAX_CANDIDATES) {
            message = Component.translatable("screen.brnquest.item_choice.too_many");
            return;
        }
        ItemStack candidate = stack.copyWithCount(1);
        boolean duplicate = listCandidates.stream().anyMatch(existing -> existing.is(candidate.getItem()));
        if (duplicate) {
            message = Component.translatable("screen.brnquest.item_choice.duplicate");
            return;
        }
        listCandidates.add(candidate);
        listCounts.add(1);
        selectedIndex = listCandidates.size() - 1;
        message = null;
    }

    /** Final panel geometry shared with JEI GUI-property registration. */
    public UiRect panelBounds() {
        int preferredHeight = editing ? EDIT_PANEL_HEIGHT : VIEW_PANEL_HEIGHT;
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, width - 8));
        int panelHeight = Math.min(preferredHeight, Math.max(1, height - 8));
        int left = (width - panelWidth) / 2;
        int top = Math.max(4, (height - panelHeight) / 2);
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    /** JEI drops onto the complete candidate viewport in editor mode. */
    public UiRect ghostTargetBounds() {
        return candidateViewport();
    }

    /** JEI exposes a drop target only for the authoring variant of this shared Screen. */
    public boolean editing() {
        return editing;
    }

    @Override
    public Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY) {
        Optional<Integer> candidate = candidateAt(mouseX, mouseY);
        if (candidate.isPresent()) {
            UiRect slot = candidateSlot(candidate.get());
            UiRect item = slot.intersection(candidateViewport());
            ItemStack stack = candidates().get(candidate.get());
            return item.containsExclusive(mouseX, mouseY) ? Optional.of(new RecipeLookupTarget(stack, item)) : Optional.empty();
        }
        if (editing) {
            int inventoryIndex = inventoryIndexAt(mouseX, mouseY);
            if (inventoryIndex >= 0 && minecraft != null && minecraft.player != null) {
                ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex);
                UiRect item = inventorySlot(inventoryIndex);
                if (!stack.isEmpty() && item.containsExclusive(mouseX, mouseY)) {
                    return Optional.of(new RecipeLookupTarget(stack, item));
                }
            }
        }
        return Optional.empty();
    }

    private void finishEditing() {
        if (!canFinish() || minecraft == null || minecraft.level == null) return;
        ItemChoiceMatcher.Spec spec;
        if (tagMode) {
            ResourceLocation tag = ResourceLocation.tryParse(tagField.getValue().strip());
            if (tag == null) {
                message = Component.translatable("screen.brnquest.item_choice.invalid_tag");
                return;
            }
            spec = new ItemChoiceMatcher.Spec(List.of(
                    ItemChoiceMatcher.Entry.tag(tag, tagRequiredCount)), 1);
        } else {
            List<ItemChoiceMatcher.Entry> entries = new ArrayList<>();
            for (int index = 0; index < listCandidates.size(); index++) {
                entries.add(ItemChoiceMatcher.Entry.item(listCandidates.get(index).copyWithCount(1)
                        .save(minecraft.level.registryAccess()).toString(), listCounts.get(index)));
            }
            spec = new ItemChoiceMatcher.Spec(entries,
                    Math.min(Math.max(1, targetRequiredEntries), entries.size()));
        }
        var normalized = ItemChoiceMatcher.normalize(minecraft.level.registryAccess(), spec.encode());
        if (normalized.result().isEmpty()) {
            message = Component.literal(normalized.error().map(error -> error.message())
                    .orElse("Invalid item matcher"));
            return;
        }
        resultConsumer.accept(normalized.result().orElseThrow());
        onClose();
    }

    private boolean canFinish() {
        if (!editing) return true;
        if (tagMode) return tagField != null && ResourceLocation.tryParse(tagField.getValue().strip()) != null;
        return !listCandidates.isEmpty();
    }

    private List<ItemStack> candidates() {
        if (!tagMode) return List.copyOf(listCandidates);
        if (minecraft == null || minecraft.level == null || tagField == null) return List.of();
        ResourceLocation tag = ResourceLocation.tryParse(tagField.getValue().strip());
        return tag == null ? List.of() : ItemChoiceMatcher.displayedCandidates(
                minecraft.level.registryAccess(), new ItemChoiceMatcher.Spec(
                        List.of(ItemChoiceMatcher.Entry.tag(tag, tagRequiredCount)), 1));
    }

    private Optional<Integer> candidateAt(double mouseX, double mouseY) {
        return candidateIndexAt(candidateViewport(), candidates().size(), renderedScroll, mouseX, mouseY);
    }

    /** Whole-slot hit testing uses the same rounded scroll offset as candidateSlot rendering. */
    static Optional<Integer> candidateIndexAt(UiRect viewport, int count, double scroll, double mouseX, double mouseY) {
        if (!viewport.containsExclusive(mouseX, mouseY)) return Optional.empty();
        int column = (int) ((mouseX - viewport.left()) / SLOT_SIZE);
        int row = (int) ((mouseY - viewport.top() + Math.round(scroll)) / SLOT_SIZE);
        int index = row * COLUMNS + column;
        return column >= 0 && column < COLUMNS && index >= 0 && index < count
                ? Optional.of(index) : Optional.empty();
    }

    private UiRect candidateSlot(int index) {
        UiRect viewport = candidateViewport();
        int left = viewport.left() + index % COLUMNS * SLOT_SIZE;
        int top = viewport.top() + index / COLUMNS * SLOT_SIZE - (int) Math.round(renderedScroll);
        return new UiRect(left, top, left + SLOT_SIZE, top + SLOT_SIZE);
    }

    private UiRect candidateViewport() {
        UiRect panel = panelBounds();
        int top = editing ? panel.top() + 69 : panel.top() + 27;
        // Reserve the status line, inventory hint/grid and bottom buttons first. At low GUI
        // heights only the candidate viewport shrinks, so scrolling and hit testing never leak
        // beneath fixed controls.
        int availableHeight = editing ? panel.height() - 193 : panel.height() - 61;
        int viewportHeight = Math.max(1, Math.min(editing ? 54 : 81, availableHeight));
        int left = panel.centerX() - COLUMNS * SLOT_SIZE / 2;
        return new UiRect(left, top, left + COLUMNS * SLOT_SIZE, top + viewportHeight);
    }

    private void updateTagFieldGeometry() {
        if (tagField == null) return;
        UiRect panel = panelBounds();
        tagField.setX(panel.left() + 64);
        tagField.setY(panel.top() + 44);
        tagField.setWidth(tagMode ? panel.width() - 145 : panel.width() - 71);
        tagField.setVisible(editing && tagMode);
        tagField.active = editing && tagMode;
    }

    private int inventoryTop() {
        return candidateViewport().bottom() + 26;
    }

    private UiRect inventorySlot(int inventoryIndex) {
        UiRect panel = panelBounds();
        int left = panel.centerX() - COLUMNS * SLOT_SIZE / 2;
        if (inventoryIndex < 9) return slot(left + inventoryIndex * SLOT_SIZE, inventoryTop() + 54);
        int main = inventoryIndex - 9;
        return slot(left + main % 9 * SLOT_SIZE, inventoryTop() + main / 9 * SLOT_SIZE);
    }

    private int inventoryIndexAt(double mouseX, double mouseY) {
        for (int index = 0; index < 36; index++) if (inventorySlot(index).containsExclusive(mouseX, mouseY)) return index;
        return -1;
    }

    private ItemStack inventoryStackAt(double mouseX, double mouseY) {
        int index = inventoryIndexAt(mouseX, mouseY);
        return index < 0 || minecraft == null || minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(index);
    }

    private void removeSelected() {
        if (selectedIndex < 0 || selectedIndex >= listCandidates.size()) return;
        listCandidates.remove(selectedIndex);
        listCounts.remove(selectedIndex);
        selectedIndex = Math.min(selectedIndex, listCandidates.size() - 1);
        targetRequiredEntries = Math.min(targetRequiredEntries, Math.max(1, listCandidates.size()));
    }

    private int displayedRequiredCount(int index) {
        if (tagMode) return tagRequiredCount;
        if (index >= 0 && index < listCounts.size()) return listCounts.get(index);
        if (original != null && index >= 0 && index < original.entries().size()) {
            return original.entries().get(index).requiredCount();
        }
        return 1;
    }

    private UiRect modeListBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.left() + 7, panel.top() + 23, panel.centerX() - 3, panel.top() + 41);
    }

    private UiRect modeTagBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.centerX() + 3, panel.top() + 23, panel.right() - 7, panel.top() + 41);
    }

    private UiRect decrementBounds() { return controlBounds(0); }
    private UiRect incrementBounds() { return controlBounds(1); }
    private UiRect moveLeftBounds() { return controlBounds(2); }
    private UiRect moveRightBounds() { return controlBounds(3); }
    private UiRect removeBounds() { return controlBounds(4); }

    private UiRect controlBounds(int index) {
        UiRect panel = panelBounds();
        int left = panel.right() - 75 + index * 14;
        return new UiRect(left, panel.top() + 44, left + 12, panel.top() + 62);
    }

    private UiRect cancelBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.left() + 7, panel.bottom() - 22, panel.centerX() - 4, panel.bottom() - 5);
    }

    private UiRect doneBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.centerX() + 4, panel.bottom() - 22, panel.right() - 7, panel.bottom() - 5);
    }

    private UiRect closeBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.left() + 7, panel.bottom() - 22, panel.right() - 7, panel.bottom() - 5);
    }

    private static UiRect slot(int left, int top) {
        return new UiRect(left, top, left + SLOT_SIZE, top + SLOT_SIZE);
    }

}

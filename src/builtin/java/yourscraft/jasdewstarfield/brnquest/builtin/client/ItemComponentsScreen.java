package yourscraft.jasdewstarfield.brnquest.builtin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.ChildScreenBackground;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.function.Consumer;

/** Edits one candidate's 1.21.1 ItemStack SNBT, including its data component patch. */
final class ItemComponentsScreen extends Screen {
    private final Screen parent;
    private final ItemStack original;
    private final Consumer<ItemStack> selection;
    private final EditorButtonInput buttons = new EditorButtonInput();
    private MultiLineEditBox editor;
    private Component issue;

    ItemComponentsScreen(Screen parent, ItemStack original, Consumer<ItemStack> selection) {
        super(Component.translatable("screen.brnquest.item_choice.components.title"));
        this.parent = parent;
        this.original = original.copyWithCount(1);
        this.selection = selection;
    }

    @Override protected void init() {
        buttons.begin();
        UiRect bounds = editorBounds();
        editor = new MultiLineEditBox(font, bounds.left(), bounds.top(), bounds.width(), bounds.height(), title, title);
        editor.setCharacterLimit(8192);
        editor.setValue(original.save(minecraft.level.registryAccess()).toString());
        addRenderableWidget(editor);
        setFocused(editor);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        UiRect panel = panelBounds();
        GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.renderItem(original, panel.left() + 12, panel.top() + 8);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 9, GraystonePalette.TEXT);
        graphics.drawString(font, Component.translatable("screen.brnquest.item_choice.components.help"),
                panel.left() + 12, panel.top() + 25, GraystonePalette.SECONDARY, false);
        if (issue != null) graphics.drawString(font,
                font.plainSubstrByWidth(issue.getString(), panel.width() - 24),
                panel.left() + 12, panel.bottom() - 45, 0xFFFF7070, false);
        buttons.render(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, applyBounds(),
                EditorButton.Definition.text(Component.translatable("gui.done"), null),
                true, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.clicked(mouseX, mouseY, button);
        if (button == 0 && cancelBounds().contains(mouseX, mouseY)) { onClose(); return true; }
        if (button == 0 && applyBounds().contains(mouseX, mouseY)) { apply(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void apply() {
        try {
            ItemStack parsed = ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(editor.getValue()));
            if (parsed.isEmpty() || !parsed.is(original.getItem())) throw new IllegalArgumentException("Item ID must stay the same");
            // Count belongs to the objective entry; the stack here supplies identity and components only.
            selection.accept(parsed.copyWithCount(1));
            onClose();
        } catch (Exception exception) {
            issue = Component.translatable("screen.brnquest.item_choice.components.invalid", exception.getMessage());
        }
    }

    private UiRect panelBounds() {
        int panelWidth = Math.min(760, Math.max(240, width - 24));
        int panelHeight = Math.min(500, Math.max(180, height - 24));
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect editorBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.left() + 12, panel.top() + 40, panel.right() - 12, panel.bottom() - 58);
    }

    private UiRect cancelBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.left() + 12, panel.bottom() - 30, panel.centerX() - 4, panel.bottom() - 10);
    }

    private UiRect applyBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.centerX() + 4, panel.bottom() - 30, panel.right() - 12, panel.bottom() - 10);
    }

    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

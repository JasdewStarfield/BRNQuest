package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.editor.RawConfigText;

import java.util.Map;
import java.util.function.Consumer;

/** Full-screen raw string-map editor used only when a registered type has no field controls. */
public final class EditorRawConfigScreen extends Screen {
    // Shared input feedback follows the same rendered geometry as every form button.
    private final EditorButtonInput buttons = new EditorButtonInput();
    private final Screen parent;
    private final Map<String, String> initialConfig;
    private final Consumer<Map<String, String>> configConsumer;
    private MultiLineEditBox editor;
    private Component issue;

    public EditorRawConfigScreen(Screen parent, Map<String, String> initialConfig,
                                 Consumer<Map<String, String>> configConsumer) {
        super(Component.translatable("screen.brnquest.editor.raw_config.title"));
        this.parent = parent;
        this.initialConfig = Map.copyOf(initialConfig);
        this.configConsumer = configConsumer;
    }

    @Override
    protected void init() {
        buttons.begin(); buttons.clearFocus();
        super.init();
        UiRect bounds = editorBounds();
        editor = new MultiLineEditBox(font, bounds.left(), bounds.top(), bounds.width(), bounds.height(),
                Component.translatable("screen.brnquest.editor.raw_config.placeholder"), title);
        // The draft protocol is the true upper bound; the parser and server impose tighter per-field limits.
        editor.setCharacterLimit(BrnQuestConstants.MAX_BOOK_BYTES);
        editor.setValue(RawConfigText.format(initialConfig));
        addRenderableWidget(editor);
        setFocused(editor);
    }

    /** Prevents Screen.render from adding a second blur pass above this screen's widgets. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void tick() {
        // The parent owns the edit lease and response reconciliation while this child owns input.
        parent.tick();
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        // Blur the completed editor before drawing the raw panel, matching the item selector lifecycle.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        UiRect panel = panelBounds();
        yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 9, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.raw_config.help"),
                panel.left() + 12, panel.top() + 25, GraystonePalette.SECONDARY, false);
        if (issue != null) {
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(issue.getString(), panel.width() - 24)),
                    panel.left() + 12, panel.bottom() - 45, 0xFFFF7070, false);
        }
        buttons.render(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, applyBounds(),
                EditorButton.Definition.text(
                        Component.translatable("screen.brnquest.editor.raw_config.apply"), null),
                true, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.clicked(mouseX, mouseY, button);
        if (button == 0 && cancelBounds().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (button == 0 && applyBounds().contains(mouseX, mouseY)) {
            applyConfig();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applyConfig() {
        try {
            Map<String, String> parsed = RawConfigText.parse(editor.getValue());
            configConsumer.accept(parsed);
            onClose();
        } catch (IllegalArgumentException exception) {
            issue = Component.translatable("screen.brnquest.editor.raw_config.invalid", exception.getMessage());
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private UiRect panelBounds() {
        int panelWidth = Math.min(760, Math.max(240, width - 24));
        int panelHeight = Math.min(500, Math.max(180, height - 24));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect editorBounds() {
        UiRect panel = panelBounds();
        return new UiRect(panel.left() + 12, panel.top() + 40, panel.right() - 12,
                panel.bottom() - 58);
    }

    private UiRect cancelBounds() {
        UiRect panel = panelBounds();
        int left = panel.left() + 12;
        return new UiRect(left, panel.bottom() - 30, left + (panel.width() - 30) / 2, panel.bottom() - 10);
    }

    private UiRect applyBounds() {
        UiRect panel = panelBounds();
        int buttonWidth = (panel.width() - 30) / 2;
        int left = panel.right() - 12 - buttonWidth;
        return new UiRect(left, panel.bottom() - 30, panel.right() - 12, panel.bottom() - 10);
    }
    /** Child editors follow the task book's pause policy instead of Screen's unconditional default. */
    @Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }

}

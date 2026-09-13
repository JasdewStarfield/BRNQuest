package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.Map;
import java.util.function.Consumer;

/** Book metadata and template remain local until the outer form is confirmed. */
final class EditorBookPropertiesScreen extends Screen {
    record Value(String fallback, QuestCreationDefaults defaults, Map<String, String> titles, BookSettings settings) {}
    private final Screen parent;
    private final Consumer<Value> selection;
    private final EditorLocalizedText text;
    private QuestCreationDefaults defaults;
    private String fallback;
    private BookSettings settings;
    private EditorTextField titleInput;
    private EditorTextField fallbackInput;
    private EditorButtonWidget done;
    EditorBookPropertiesScreen(Screen parent, QuestBookDefinition book, String locale, Consumer<Value> selection) {
        super(Component.translatable("screen.brnquest.book.properties"));
        this.parent = parent; this.selection = selection;
        text = new EditorLocalizedText(book.localization(), "title", book.title(), locale);
        settings = book.settings();
        defaults = book.questDefaults(); fallback = book.localization().fallbackLocale();
    }
    @Override protected void init() {
        int panelWidth = Math.min(340, width - 24);
        int left = (width - panelWidth) / 2, top = height / 2 - 90;
        if (titleInput == null) {
            titleInput = new EditorTextField(font, title, 256); titleInput.setValue(text.value());
            titleInput.setResponder(text::remember);
        }
        titleInput.show(new UiRect(left + 85, top + 30, left + panelWidth - 70, top + 50), true);
        addRenderableWidget(titleInput);
        var language = addRenderableWidget(new EditorButtonWidget(left + panelWidth - 64, top + 30, 64, 20, Component.literal(text.locale()), button -> {
            minecraft.setScreen(new EditorLocaleScreen(this, text.locales(), text.locale(), locale -> {
                text.select(locale); titleInput.setValue(text.value());
            }));
        }));
        String source = text.source();
        language.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                source.isEmpty() ? "screen.brnquest.editor.locale.native" : source.equals(text.locale())
                        ? "screen.brnquest.editor.locale.current" : "screen.brnquest.editor.locale.fallback", source)));
        fallbackInput = new EditorTextField(font, Component.translatable("screen.brnquest.book.fallback"), 16);
        fallbackInput.setValue(fallback);
        fallbackInput.setResponder(value -> { fallback = value; if (done != null) done.active = EditorLocalizedText.validLocale(value); });
        fallbackInput.show(new UiRect(left + 85, top + 60, left + panelWidth, top + 80), true); addRenderableWidget(fallbackInput);
        addRenderableWidget(new EditorButtonWidget(left, top + 92, panelWidth, 20, Component.translatable("screen.brnquest.defaults.title"), button ->
                minecraft.setScreen(new EditorCreationDefaultsScreen(this, defaults, QuestCreationDefaults.EMPTY, value -> defaults = value))));
        addRenderableWidget(new EditorButtonWidget(left, top + 118, panelWidth, 20,
                Component.translatable("screen.brnquest.book.settings"), button ->
                minecraft.setScreen(new EditorBookSettingsScreen(this, settings, value -> settings = value))));
        addRenderableWidget(new EditorButtonWidget(left, top + 156, panelWidth / 2 - 4, 20, Component.translatable("gui.cancel"), button -> onClose()));
        done = addRenderableWidget(new EditorButtonWidget(left + panelWidth / 2 + 4, top + 156, panelWidth / 2 - 4, 20, Component.translatable("gui.done"), button -> {
            selection.accept(new Value(BookLocalization.normalizeLocale(fallback.strip()), defaults, text.changes(), settings)); onClose();
        }));
        done.active = EditorLocalizedText.validLocale(fallback);
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 1000);
        try {
            int panelWidth = Math.min(340, width - 24);
        int left = (width - panelWidth) / 2, top = height / 2 - 90;
            graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
            GraystoneSurface.raised(graphics, new UiRect(left - 10, top, left + panelWidth + 10, top + 188), GraystonePalette.PANEL, true);
            graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFFFF);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.structure.title"), left, top + 36, GraystonePalette.SECONDARY, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.brnquest.book.fallback").getString(), 80), left, top + 66, GraystonePalette.SECONDARY, false);
            titleInput.setSuggestion(titleInput.getValue().isEmpty() ? font.plainSubstrByWidth(text.placeholder(), titleInput.getWidth() - 8) : null);
            if (!done.active) graphics.drawCenteredString(font, Component.translatable("screen.brnquest.book.invalid_locale"),
                    width / 2, top + 142, 0xFFFF8080);
            super.render(graphics, x, y, partial); graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

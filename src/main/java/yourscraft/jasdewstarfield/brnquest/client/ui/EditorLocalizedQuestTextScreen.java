package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Locale-switching quest text editor with a real multiline description field. */
public final class EditorLocalizedQuestTextScreen extends Screen {
    public record Value(String locale, String title, String subtitle, String description) {}

    // Shared input feedback follows the same rendered geometry as every form button.
    private final EditorButtonInput buttons = new EditorButtonInput();
    private final Screen parent;
    private final BookLocalization localization;
    private final QuestDefinition quest;
    private final Consumer<Value> consumer;
    private final List<String> locales;
    private int localeIndex;
    private EditBox titleEditor;
    private EditBox subtitleEditor;
    private EditBox localeEditor;
    private MultiLineEditBox descriptionEditor;

    public EditorLocalizedQuestTextScreen(Screen parent, BookLocalization localization, QuestDefinition quest,
                                          String clientLocale, Consumer<Value> consumer) {
        super(Component.translatable("screen.brnquest.editor.localized_text.title"));
        this.parent = parent;
        this.localization = localization;
        this.quest = quest;
        this.consumer = consumer;
        LinkedHashSet<String> available = new LinkedHashSet<>();
        available.add(BookLocalization.normalizeLocale(clientLocale));
        available.add(localization.fallbackLocale());
        available.addAll(localization.translations().keySet().stream().sorted().toList());
        this.locales = new ArrayList<>(available);
    }

    @Override
    protected void init() {
        buttons.begin(); buttons.clearFocus();
        super.init();
        UiRect panel = panelBounds();
        titleEditor = new EditBox(font, panel.left() + 88, panel.top() + 46, panel.width() - 100, 20,
                Component.translatable("screen.brnquest.editor.quest.title"));
        titleEditor.setMaxLength(256);
        subtitleEditor = new EditBox(font, panel.left() + 88, panel.top() + 72, panel.width() - 100, 20,
                Component.translatable("screen.brnquest.editor.quest.subtitle"));
        subtitleEditor.setMaxLength(256);
        UiRect newLocale = newLocaleBounds();
        localeEditor = new EditBox(font, newLocale.left(), newLocale.top(), newLocale.width(), newLocale.height(),
                Component.translatable("screen.brnquest.editor.localized_text.new_locale"));
        localeEditor.setMaxLength(32);
        localeEditor.setHint(Component.translatable("screen.brnquest.editor.localized_text.new_locale_hint"));
        UiRect description = descriptionBounds();
        descriptionEditor = new MultiLineEditBox(font, description.left(), description.top(), description.width(),
                description.height(), Component.translatable("screen.brnquest.editor.multiline.placeholder"),
                Component.translatable("screen.brnquest.editor.quest.description"));
        descriptionEditor.setCharacterLimit(32_768);
        addRenderableWidget(titleEditor);
        addRenderableWidget(subtitleEditor);
        addRenderableWidget(localeEditor);
        addRenderableWidget(descriptionEditor);
        loadLocale();
        setFocused(titleEditor);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void tick() { parent.tick(); super.tick(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x70151820);
        UiRect panel = panelBounds();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xF0202632);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 9, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.localized_text.locale"),
                panel.left() + 12, panel.top() + 27, 0xFF9FB0C2, false);
        buttons.render(graphics, font, localeBounds(),
                EditorButton.Definition.text(Component.literal(locales.get(localeIndex)),
                        Component.translatable("screen.brnquest.editor.localized_text.switch_locale")),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, addLocaleBounds(),
                EditorButton.Definition.text(Component.literal("+"),
                        Component.translatable("screen.brnquest.editor.localized_text.add_locale")),
                true, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.title"),
                panel.left() + 12, panel.top() + 52, 0xFF9FB0C2, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.subtitle"),
                panel.left() + 12, panel.top() + 78, 0xFF9FB0C2, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.description"),
                panel.left() + 12, panel.top() + 103, 0xFF9FB0C2, false);
        buttons.render(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null), true, false,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, applyBounds(),
                EditorButton.Definition.text(Component.translatable("gui.done"), null), true, false,
                EditorButton.Tone.PRIMARY, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.clicked(mouseX, mouseY, button);
        if (button == 0 && localeBounds().contains(mouseX, mouseY)) {
            localeIndex = (localeIndex + 1) % locales.size();
            localeEditor.setTextColor(0xFFFFFFFF);
            loadLocale();
            return true;
        }
        if (button == 0 && addLocaleBounds().contains(mouseX, mouseY)) {
            addLocale();
            return true;
        }
        if (button == 0 && cancelBounds().contains(mouseX, mouseY)) { onClose(); return true; }
        if (button == 0 && applyBounds().contains(mouseX, mouseY)) {
            consumer.accept(new Value(locales.get(localeIndex), titleEditor.getValue(), subtitleEditor.getValue(),
                    descriptionEditor.getValue()));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && getFocused() == localeEditor) {
            addLocale();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addLocale() {
        String raw = localeEditor.getValue().strip();
        if (!validLocaleCode(raw)) {
            localeEditor.setTextColor(0xFFFF8B8B);
            return;
        }
        String locale = BookLocalization.normalizeLocale(raw);
        int existing = locales.indexOf(locale);
        if (existing < 0) {
            locales.add(locale);
            localeIndex = locales.size() - 1;
        } else {
            localeIndex = existing;
        }
        localeEditor.setValue("");
        localeEditor.setTextColor(0xFFFFFFFF);
        loadLocale();
        setFocused(titleEditor);
    }

    static boolean validLocaleCode(String value) {
        return value != null && value.length() <= 32
                && value.matches("[A-Za-z0-9]{2,16}([_-][A-Za-z0-9]{2,16})*");
    }

    private void loadLocale() {
        String locale = locales.get(localeIndex);
        Map<String, String> values = localization.translations().getOrDefault(locale, Map.of());
        String sourceId = quest.legacyId().isBlank() ? quest.id().toString() : quest.legacyId();
        String prefix = "quest." + sourceId + ".";
        boolean nativeFallback = locale.equals(localization.fallbackLocale());
        titleEditor.setValue(values.getOrDefault(prefix + "title", nativeFallback ? quest.title() : ""));
        subtitleEditor.setValue(values.getOrDefault(prefix + "quest_subtitle", nativeFallback ? quest.subtitle() : ""));
        descriptionEditor.setValue(values.getOrDefault(prefix + "quest_desc", nativeFallback ? quest.description() : ""));
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    private UiRect panelBounds() {
        int panelWidth = Math.min(760, Math.max(260, width - 24));
        int panelHeight = Math.min(520, Math.max(230, height - 24));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect localeBounds() { UiRect p = panelBounds(); return new UiRect(p.left() + 88, p.top() + 22, p.centerX() - 4, p.top() + 42); }
    private UiRect newLocaleBounds() { UiRect p = panelBounds(); return new UiRect(p.centerX() + 4, p.top() + 22, p.right() - 38, p.top() + 42); }
    private UiRect addLocaleBounds() { UiRect p = panelBounds(); return new UiRect(p.right() - 34, p.top() + 22, p.right() - 12, p.top() + 42); }
    private UiRect descriptionBounds() { UiRect p = panelBounds(); return new UiRect(p.left() + 12, p.top() + 116, p.right() - 12, p.bottom() - 48); }
    private UiRect cancelBounds() { UiRect p = panelBounds(); return new UiRect(p.left() + 12, p.bottom() - 34, p.centerX() - 4, p.bottom() - 10); }
    private UiRect applyBounds() { UiRect p = panelBounds(); return new UiRect(p.centerX() + 4, p.bottom() - 34, p.right() - 12, p.bottom() - 10); }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorLocalizedText;
import yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorQuickTextDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import java.util.function.BooleanSupplier;

/** Localized text buffers and recovery belong to one editor opening and its lease. */
final class QuestTextEditor {
    interface Host {
        EditorSessionView session();
        QuestBookSnapshot snapshot();
        QuestScreenLayout layout();
        EditorButtonInput buttons();
        EditorChildScreens children();
        String locale();
        void focus(EditorTextField field);
        void closeOverlay();
        void nativeClick(double x, double y, int button);
        void tooltip(List<Component> lines);
        void text(GuiGraphics g, UiRect bounds, Component label, Component tooltip, boolean enabled, EditorButton.Tone tone, int x, int y);
        boolean mutate(EditorMutationIntent intent);
        void submitted(ResourceLocation quest);
    }
    private final Host host;
    private final EditorOverlayHost editorOverlays;
    private final EditorFormIdentity identity = new EditorFormIdentity();
    private Font font;
    QuestTextEditor(Host host, EditorOverlayHost overlays) { this.host = host; editorOverlays = overlays; }
    void bind(Font font, Consumer<EditorTextField> register) {
        this.font = font;
        if (quickTextField == null) quickTextField = new EditorTextField(font, Component.translatable("screen.brnquest.editor.quick_edit.input"), 256);
        quickTextField.hide(); quickTextField.setNativeFormatting(true); register.accept(quickTextField);
    }
    void sessionChanged() { identity.close(); pendingLocalizedQuestText = null; closeQuickTextEditor(); }
    private boolean current(EditorFormIdentity.Token token) {
        var editor = host.session();
        return token != null && identity.matches(token, editor.sessionId(), token.target())
                && editor.editable() && !editor.busy();
    }
    private record PendingLocalizedQuestText(ResourceLocation questId, EditorLocalizedQuestTextScreen.Value value) {}
    private EditorTextField quickTextField;
    private EditorLocalizedText quickLocalizedText;
    private PendingLocalizedQuestText pendingLocalizedQuestText;
    private Kind quickTextKind = Kind.NONE;
    private ResourceLocation quickTextQuestId;
    private Component quickTextIssue;
    void openQuickTextEditor(QuestDefinition quest, Kind kind) {
        if (kind == Kind.NONE || host.session().busy() || !host.session().editable()) return;
        if (kind == Kind.DESCRIPTION) {
            openLocalizedQuestTextEditor(quest);
            return;
        }
        host.closeOverlay();
        identity.open(host.session().sessionId(), quest.id());
        quickTextKind = kind;
        quickTextQuestId = quest.id();
        quickTextIssue = null;
        quickLocalizedText = new EditorLocalizedText(host.snapshot().book().localization(),
                BookText.questPrefix(quest) + (kind == Kind.TITLE ? "title" : "quest_subtitle"),
                kind == Kind.TITLE ? quest.title() : quest.subtitle(), host.locale());
        quickTextField.setValue(quickLocalizedText.value());
        editorOverlays.show(EditorOverlayHost.Kind.QUICK_TEXT);
        host.focus(quickTextField);
    }

    void openLocalizedQuestTextEditor(QuestDefinition quest) { openLocalizedQuestTextEditor(quest, () -> true); }
    void openLocalizedQuestTextEditor(QuestDefinition quest, BooleanSupplier parentCurrent) {
        var snapshot = host.snapshot();
        var editor = host.session();
        if (snapshot == null || editor.busy() || !editor.editable()) return;
        identity.open(editor.sessionId(), quest.id());
        var token = identity.token();
        var recovery = pendingLocalizedQuestText != null && pendingLocalizedQuestText.questId().equals(quest.id())
                ? pendingLocalizedQuestText.value() : null;
        host.children().localized(snapshot.book().localization(), quest, host.locale(),
                value -> current(token) && parentCurrent.getAsBoolean() && submitLocalizedQuestText(quest.id(), value),
                recovery, () -> { if (current(token) && parentCurrent.getAsBoolean()) discardLocalizedQuestText(quest.id()); });
    }

    private boolean submitLocalizedQuestText(ResourceLocation questId, EditorLocalizedQuestTextScreen.Value value) {
        if (host.mutate(new EditorMutationIntent("UPDATE_QUEST_TRANSLATION", questId, null, null, value.locale(), 0, 0, 0, List.of(),
                Map.of("title", value.title(), "subtitle", value.subtitle(), "description", value.description(),
                        "description_format", value.descriptionFormat().serializedName())))) {
            host.submitted(questId);
            // Keep the exact submitted buffer until the verified server draft agrees; rejection can reopen it.
            pendingLocalizedQuestText = new PendingLocalizedQuestText(questId, value);
            return true;
        }
        return false;
    }

    private void discardLocalizedQuestText(ResourceLocation questId) {
        if (pendingLocalizedQuestText != null && pendingLocalizedQuestText.questId().equals(questId))
            pendingLocalizedQuestText = null;
    }

    void reconcileLocalizedQuestText(EditorSessionView editor) {
        if (pendingLocalizedQuestText == null || editor.busy() || editor.mode() == ClientEditorState.Mode.ERROR) return;
        QuestBookSnapshot snapshot = editor.draft().orElse(null);
        if (snapshot == null) return;
        QuestDefinition quest = snapshot.quests().get(pendingLocalizedQuestText.questId());
        if (quest == null) return;
        if (LocalizedQuestTextRecovery.matches(snapshot.book(), quest, pendingLocalizedQuestText.value()))
            pendingLocalizedQuestText = null;
    }

    void renderQuickTextEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        if (quickTextKind == Kind.NONE || quickTextField == null) return;
        Component fieldName = Component.translatable(quickTextKind.translationKey());
        EditorQuickTextDialog.render(graphics, font, host.layout(), host.buttons(),
                Component.translatable("screen.brnquest.editor.quick_edit.heading", fieldName),
                quickTextIssue, !host.session().busy(), mouseX, mouseY);
        quickLocalizedText.remember(quickTextField.getValue());
        UiRect textRow = EditorQuickTextDialog.layout(host.layout()).input();
        quickTextField.show(EditorLocalizedText.inputBounds(textRow), !host.session().busy());
        showLocalizedPlaceholder(quickTextField, quickLocalizedText);
        renderLocaleButton(graphics, quickLocalizedText, textRow, mouseX, mouseY);
        // The editor chrome is translated to the final overlay depth. Rendering this field through
        // Screen's ordinary renderable list would leave its border and text behind the modal panel.
        quickTextField.render(graphics, mouseX, mouseY, 0.0F);
    }

    boolean handleQuickTextEditorClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!host.session().busy() && EditorLocalizedText.languageBounds(
                    EditorQuickTextDialog.layout(host.layout()).input()).contains(mouseX, mouseY)) {
                openLocalePicker(quickLocalizedText, quickTextField);
                return true;
            }
            EditorQuickTextDialog.Action action = EditorQuickTextDialog.actionAt(host.layout(), mouseX, mouseY);
            if (action == EditorQuickTextDialog.Action.CANCEL) {
                closeQuickTextEditor();
                return true;
            }
            if (action == EditorQuickTextDialog.Action.APPLY) {
                submitQuickTextEdit();
                return true;
            }
        }
        host.nativeClick(mouseX, mouseY, button);
        return true;
    }

    void submitQuickTextEdit() {
        QuestBookSnapshot snapshot = host.snapshot();
        QuestDefinition quest = snapshot == null || quickTextQuestId == null
                ? null : snapshot.quests().get(quickTextQuestId);
        if (host.session().busy() || quest == null || quickTextKind == Kind.NONE
                || !host.session().editable()) return;
        quickLocalizedText.remember(quickTextField.getValue());
        if (quickLocalizedText.changes().isEmpty()) { closeQuickTextEditor(); return; }
        if (host.mutate(new EditorMutationIntent("UPDATE_QUEST_TRANSLATION", quest.id(), null, null, quickLocalizedText.locale(), 0, 0, 0,
                List.of(), localizedConfig(quickLocalizedText,
                        quickTextKind == Kind.TITLE ? "title" : "quest_subtitle", Map.of())))) {
            host.submitted(quest.id());
            closeQuickTextEditor();
        }
    }

    void closeQuickTextEditor() {
        identity.close();
        quickTextKind = Kind.NONE;
        quickLocalizedText = null;
        quickTextQuestId = null;
        quickTextIssue = null;
        host.focus(null);
        if (quickTextField != null) quickTextField.hide();
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) editorOverlays.close();
    }

    private void showLocalizedPlaceholder(EditorTextField field, EditorLocalizedText text) {
        // Native suggestions are gray and remain visible with focus, but are never selected or submitted.
        field.setSuggestion(field.getValue().isEmpty() && text != null
                ? font.plainSubstrByWidth(text.placeholder(), Math.max(0, field.getWidth() - 8)) : null);
    }

    private void renderLocaleButton(GuiGraphics graphics, EditorLocalizedText text, UiRect row, int mouseX, int mouseY) {
        if (text == null) return;
        String source = text.source();
        Component tooltip = Component.translatable(source.isEmpty() ? "screen.brnquest.editor.locale.native"
                : source.equals(text.locale()) ? "screen.brnquest.editor.locale.current" : "screen.brnquest.editor.locale.fallback", source);
        host.text(graphics, EditorLocalizedText.languageBounds(row), Component.literal(text.locale()),
                tooltip, !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void openLocalePicker(EditorLocalizedText text, EditorTextField field) {
        if (text == null) return;
        text.remember(field.getValue());
        var token = identity.token();
        host.children().locale(text.locales(), text.locale(), locale -> {
            if (!current(token)) return;
            text.select(locale); quickTextField.setValue(text.value());
        });
    }

    private Map<String, String> localizedConfig(EditorLocalizedText text, String field, Map<String, String> metadata) {
        var result = new java.util.TreeMap<String, String>(metadata);
        result.put(LocalizedSingleLineEdits.FIELD, field);
        text.changes().forEach((locale, value) -> result.put(LocalizedSingleLineEdits.PREFIX + locale, value));
        return Map.copyOf(result);
    }

    enum Kind {
        NONE(""),
        TITLE("screen.brnquest.editor.quest.title"),
        SUBTITLE("screen.brnquest.editor.quest.subtitle"),
        DESCRIPTION("screen.brnquest.editor.quest.description");

        private final String translationKey;

        Kind(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }
    }

}

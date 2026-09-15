package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorActionGroup;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorEntryListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Owns typed-entry list rendering and hit geometry; semantic actions are returned to the parent. */
final class QuestTypedEntryListSection {
    enum Action { EDIT, MORE, ADD, PASTE, DONE }

    record Intent(Action action, ResourceLocation entryId, int x, int y) {}

    record Model(QuestScreenFrameIdentity identity, ResourceLocation questId, QuestTypedEntryKind kind, Component heading,
                 Component message, boolean enabled, int count, IntFunction<ResourceLocation> idAt,
                 Function<EditorListPanel.Row<ResourceLocation>, EditorEntryListPanel.Content> contentAt, boolean canPaste) {
        Model(QuestScreenFrameIdentity identity, ResourceLocation questId, QuestTypedEntryKind kind, Component heading,
              Component message, boolean enabled, int count, IntFunction<ResourceLocation> idAt,
              Function<EditorListPanel.Row<ResourceLocation>, EditorEntryListPanel.Content> contentAt) {
            this(identity, questId, kind, heading, message, enabled, count, idAt, contentAt, false);
        }
    }

    record Layout(int drawerOffset, UiRect panel, UiRect list, UiRect clip, int trackX,
                  UiRect add, UiRect done, int messageWidth) {}

    record RenderResult(EditorEntryListPanel.Hover hover) {}

    private record ActionKey(ResourceLocation entryId, Action action) {}
    private record Frame(QuestScreenFrameIdentity identity, ResourceLocation questId, QuestTypedEntryKind kind) {}

    private final EditorEntryListPanel<ResourceLocation, ActionKey> entries = new EditorEntryListPanel<>();
    private Frame frame;
    private Intent pendingIntent;

    RenderResult render(GuiGraphics graphics, Font font, Model model, Layout layout,
                        int rowHeight, double seconds, double speed, int mouseX, int mouseY) {
        frame = new Frame(model.identity(), model.questId(), model.kind());
        pendingIntent = null;
        graphics.pose().pushPose();
        graphics.pose().translate(-layout.drawerOffset(), 0, 0);
        try {
            graphics.fill(layout.panel().left(), layout.panel().top(), layout.panel().right(), layout.panel().bottom(),
                    GraystonePalette.PANEL);
            graphics.drawString(font, model.heading(), layout.list().left(), layout.panel().top() + 8,
                    0xFFFFFFFF, false);
            if (model.message() != null) {
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                                model.message().getString(), layout.messageWidth())),
                        layout.list().left(), layout.add().top() - 12, 0xFFFFA070, false);
            }

            List<EditorActionGroup.Placed<ActionKey>> footer = new ArrayList<>();
            Component add = Component.translatable("screen.brnquest.editor.typed.add");
            footer.add(new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(
                    new ActionKey(null, Action.ADD), EditorButton.Definition.iconAndText(add, add,
                    QuestActionIcons.named("plus")), model.enabled(), EditorButton.Tone.PRIMARY,
                    (x, y) -> pendingIntent = new Intent(Action.ADD, null, x.intValue(), y.intValue())),
                    new UiRect(layout.add().left(), layout.add().top(), layout.add().centerX() - 2, layout.add().bottom()), layout.clip()));
            // Text-only paste action reuses the footer's keyboard focus and click geometry.
            footer.add(new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(
                    new ActionKey(null, Action.PASTE), EditorButton.Definition.text(
                    Component.translatable("screen.brnquest.clipboard.paste"), Component.translatable("screen.brnquest.clipboard.paste_help")),
                    model.enabled() && model.canPaste(), EditorButton.Tone.PRIMARY,
                    (x, y) -> pendingIntent = new Intent(Action.PASTE, null, x.intValue(), y.intValue())),
                    new UiRect(layout.add().centerX() + 2, layout.add().top(), layout.add().right(), layout.add().bottom()), layout.clip()));
            footer.add(new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(
                    new ActionKey(null, Action.DONE), EditorButton.Definition.text(
                    Component.translatable("gui.done"), null), true, EditorButton.Tone.NEUTRAL,
                    (x, y) -> pendingIntent = new Intent(Action.DONE, null, x.intValue(), y.intValue())),
                    layout.done(), layout.clip()));

            EditorEntryListPanel.Hover hover = entries.render(graphics, font, layout.list(), layout.clip(),
                    layout.trackX(), rowHeight, model.count(), model.idAt(), seconds, speed, model.contentAt(),
                    id -> List.of(entryAction(id, Action.EDIT, "✎", model.enabled()),
                            entryAction(id, Action.MORE, "⋯", model.enabled())), footer,
                    Component.translatable("screen.brnquest.editor.typed.empty"), mouseX, mouseY);
            return new RenderResult(hover);
        } finally {
            graphics.pose().popPose();
        }
    }

    private EditorActionGroup.Action<ActionKey> entryAction(ResourceLocation id, Action action,
                                                              String glyph, boolean enabled) {
        String suffix = action == Action.EDIT ? "edit" : "more";
        Component label = Component.translatable("screen.brnquest.editor.action." + suffix);
        return new EditorActionGroup.Action<>(new ActionKey(id, action),
                EditorButton.Definition.iconOnly(label, label, EditorIcon.glyph(Component.literal(glyph))),
                enabled, EditorButton.Tone.PRIMARY,
                (x, y) -> pendingIntent = new Intent(action, id, x.intValue(), y.intValue()));
    }

    Optional<Intent> mouseClicked(QuestScreenFrameIdentity current, ResourceLocation questId, QuestTypedEntryKind kind,
                                  boolean inputAllowed, boolean busy, double x, double y, int button) {
        pendingIntent = null;
        if (button != 0 && button != 1 || !accepts(current, questId, kind) || !inputAllowed) return Optional.empty();
        if (entries.list().mouseClicked(x, y, button)) return Optional.empty();
        // The Done footer remains enabled while a request is pending; individual actions own their enabled state.
        if (entries.actions().mouseClicked(x, y, button)) return takeIntent();
        if (busy) return Optional.empty();
        entries.list().rowAt(x, y).ifPresent(row -> pendingIntent = new Intent(
                button == 1 ? Action.MORE : Action.EDIT, row.key(), (int) x, (int) y));
        return takeIntent();
    }

    boolean mouseScrolled(QuestScreenFrameIdentity current, ResourceLocation questId, QuestTypedEntryKind kind,
                          boolean inputAllowed,
                          double x, double y, double delta, double step) {
        return accepts(current, questId, kind) && inputAllowed && entries.list().mouseScrolled(x, y, delta, step);
    }

    boolean mouseDragged(double y, int button) { return entries.list().mouseDragged(y, button); }
    boolean mouseReleased(int button) { return entries.list().mouseReleased(button); }

    boolean focusNext(QuestScreenFrameIdentity current, ResourceLocation questId, QuestTypedEntryKind kind,
                      boolean inputAllowed, boolean backwards) {
        if (!accepts(current, questId, kind) || !inputAllowed) return false;
        entries.actions().focusNext(backwards);
        return true;
    }

    Optional<Intent> activateFocused(QuestScreenFrameIdentity current, ResourceLocation questId,
                                     QuestTypedEntryKind kind,
                                     boolean inputAllowed) {
        pendingIntent = null;
        if (!accepts(current, questId, kind) || !inputAllowed) return Optional.empty();
        entries.actions().activateFocused();
        return takeIntent();
    }

    Optional<Component> narration(QuestScreenFrameIdentity current, ResourceLocation questId,
                                  QuestTypedEntryKind kind, boolean inputAllowed) {
        return accepts(current, questId, kind) && inputAllowed ? entries.actions().narration() : Optional.empty();
    }

    /** A focused row is the keyboard copy target; footer focus never silently copies another row. */
    Optional<ResourceLocation> focusedEntry() {
        return entries.actions().focusedKey().map(ActionKey::entryId);
    }

    void invalidate() {
        frame = null;
        pendingIntent = null;
        entries.invalidate();
    }

    void reset() {
        frame = null;
        pendingIntent = null;
        entries.reset();
    }

    private boolean accepts(QuestScreenFrameIdentity current, ResourceLocation questId, QuestTypedEntryKind kind) {
        return frame != null && acceptsFrame(frame.identity(), frame.questId(), frame.kind(), current, questId, kind);
    }

    static boolean acceptsFrame(QuestScreenFrameIdentity rendered, ResourceLocation renderedQuest,
                                QuestTypedEntryKind renderedKind, QuestScreenFrameIdentity current,
                                ResourceLocation currentQuest, QuestTypedEntryKind currentKind) {
        return rendered.equals(current) && renderedQuest.equals(currentQuest) && renderedKind == currentKind;
    }

    private Optional<Intent> takeIntent() {
        Intent result = pendingIntent;
        pendingIntent = null;
        return Optional.ofNullable(result);
    }
}

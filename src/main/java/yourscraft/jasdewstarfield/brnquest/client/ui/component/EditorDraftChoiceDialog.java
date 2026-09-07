package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Presents the explicit boundary between continuing a saved draft and versioning the active book. */
public final class EditorDraftChoiceDialog {
    public enum Action { NONE, CANCEL, CONTINUE, CREATE_FROM_ACTIVE }

    public record Layout(UiRect dialog, UiRect cancel, UiRect continueDraft, UiRect createFromActive) {}

    private EditorDraftChoiceDialog() {}

    public static Layout layout(QuestScreenLayout screenLayout, boolean existingDraft) {
        UiRect dialog = screenLayout.centeredDialog(420, 280, 20, 104);
        int top = dialog.bottom() - 28;
        if (!existingDraft) {
            UiRect cancel = new UiRect(dialog.left() + 10, top, dialog.centerX() - 4, dialog.bottom() - 8);
            UiRect create = new UiRect(dialog.centerX() + 4, top, dialog.right() - 10, dialog.bottom() - 8);
            return new Layout(dialog, cancel, new UiRect(0, 0, 0, 0), create);
        }
        int available = dialog.width() - 28;
        int slot = available / 3;
        UiRect cancel = new UiRect(dialog.left() + 8, top, dialog.left() + 8 + slot, dialog.bottom() - 8);
        UiRect continueDraft = new UiRect(cancel.right() + 6, top, cancel.right() + 6 + slot,
                dialog.bottom() - 8);
        UiRect create = new UiRect(continueDraft.right() + 6, top, dialog.right() - 8, dialog.bottom() - 8);
        return new Layout(dialog, cancel, continueDraft, create);
    }

    public static void render(GuiGraphics graphics, Font font, QuestScreenLayout screenLayout,
                              boolean existingDraft, Component draftTitle, int mouseX, int mouseY) {
        Layout layout = layout(screenLayout, existingDraft);
        UiRect dialog = layout.dialog();
        graphics.fill(0, 0, screenLayout.width(), screenLayout.height(), 0x88000000);
        graphics.fill(dialog.left(), dialog.top(), dialog.right(), dialog.bottom(), 0xFF202832);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.draft_choice.title"),
                dialog.centerX(), dialog.top() + 9, 0xFFFFFFFF);
        Component detail = Component.translatable(existingDraft
                ? "screen.brnquest.editor.draft_choice.existing_detail"
                : "screen.brnquest.editor.draft_choice.new_detail");
        graphics.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(detail.getString(),
                dialog.width() - 20)), dialog.centerX(), dialog.top() + 27, 0xFFB7C5D8);
        if (existingDraft && draftTitle != null) {
            String visible = font.plainSubstrByWidth(draftTitle.getString(), dialog.width() - 36);
            graphics.drawCenteredString(font, Component.literal(visible), dialog.centerX(), dialog.top() + 43,
                    0xFFFFC06A);
        }
        renderButton(graphics, font, layout.cancel(), "gui.cancel", EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        if (existingDraft) {
            renderButton(graphics, font, layout.continueDraft(),
                    "screen.brnquest.editor.draft_choice.continue", EditorButton.Tone.PRIMARY, mouseX, mouseY);
        }
        renderButton(graphics, font, layout.createFromActive(), existingDraft
                        ? "screen.brnquest.editor.draft_choice.version" : "screen.brnquest.editor.draft_choice.create",
                existingDraft ? EditorButton.Tone.WARNING : EditorButton.Tone.SUCCESS, mouseX, mouseY);
    }

    private static void renderButton(GuiGraphics graphics, Font font, UiRect bounds, String key,
                                     EditorButton.Tone tone, int mouseX, int mouseY) {
        EditorButton.renderInteractive(graphics, font, bounds,
                EditorButton.Definition.text(Component.translatable(key), null), true, false, tone, mouseX, mouseY);
    }

    public static Action actionAt(QuestScreenLayout screenLayout, boolean existingDraft,
                                  double mouseX, double mouseY) {
        Layout layout = layout(screenLayout, existingDraft);
        if (layout.cancel().contains(mouseX, mouseY)) return Action.CANCEL;
        if (existingDraft && layout.continueDraft().contains(mouseX, mouseY)) return Action.CONTINUE;
        if (layout.createFromActive().contains(mouseX, mouseY)) return Action.CREATE_FROM_ACTIVE;
        return Action.NONE;
    }
}

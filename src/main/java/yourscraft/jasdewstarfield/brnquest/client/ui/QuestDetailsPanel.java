package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detail section composition. The caller supplies row renderers; this panel owns text flow and scrolling. */
final class QuestDetailsPanel {
    private static final EditorIcon TASK_SECTION_ICON = EditorIcon.sprite(
            net.minecraft.resources.ResourceLocation.parse("brnquest:editor/type/checkmark"));
    private static final EditorIcon REWARD_SECTION_ICON = EditorIcon.sprite(
            net.minecraft.resources.ResourceLocation.parse("brnquest:editor/type/reward_table"));
    record Layout(UiRect content, UiRect clip, int trackX) {}
    record Model(QuestDefinition quest, String title, String subtitle, String description,
                 QuestStatus status, Component statusText,
                 int statusColor, boolean editing, boolean gameplay, boolean ready, boolean suppressAutoClaim) {}
    interface Rows {
        int task(TaskDefinition task, int x, int y, int width);
        default int relations(int x, int y, int width) { return y; }
        void reward(RewardDefinition reward, int x, int y);
        default int rewardWidth(RewardDefinition reward) { return QuestViewportMath.REWARD_ROW_HEIGHT; }
    }
    record Result(Map<String, UiRect> textAreas, UiRect completeAction, UiRect trackAction,
                  Component hint, boolean lockedStatusHovered) {}
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private int contentHeight;
    private double drawnScroll;
    EditorSmoothScroll scroll() { return scroll; }
    int contentHeight() { return contentHeight; }
    void reset() { scroll.snap(0); contentHeight = 0; }

    Result render(GuiGraphics graphics, Font font, Layout layout, Model model, Rows rows,
                  int mouseX, int mouseY, double seconds, double speed) {
        QuestDefinition quest = model.quest();
        QuestStatus status = model.status();
        boolean editing = model.editing();
        Map<String, UiRect> textAreas = new LinkedHashMap<>();
        Component hint = null;
        int contentLeft = layout.content().left();
        int contentWidth = layout.content().width();
        int viewportHeight = Math.max(1, layout.content().height());
        drawnScroll = scroll.frameAndRender(graphics, layout.trackX(), layout.content().top(),
                layout.content().bottom(), contentHeight, viewportHeight,
                seconds, speed);
        int y = layout.content().top() - (int) Math.round(drawnScroll);
        UiRect clip = layout.clip();
        graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        int titleTop = y;
        y = EditorTextRenderer.drawWrapped(graphics, font, model.title(), contentLeft, y, contentWidth - 14, 0xFFFFFF);
        if (editing) addTextArea(textAreas, layout.content(), "TITLE", contentLeft, titleTop, contentWidth - 14, y);
        y += 3;

        if (editing || !model.subtitle().isBlank()) {
            int subtitleTop = y;
            String subtitle = model.subtitle().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_subtitle").getString()
                    : model.subtitle();
            y = EditorTextRenderer.drawWrapped(graphics, font, subtitle, contentLeft, y, contentWidth,
                    model.subtitle().isBlank() ? GraystonePalette.DISABLED : GraystonePalette.SECONDARY);
            if (editing) addTextArea(textAreas, layout.content(), "SUBTITLE", contentLeft, subtitleTop, contentWidth, y);
            y += 4;
        }

        boolean ready = model.ready();
        Component statusText = model.statusText();
        // Share the status row with tracking and relation navigation instead of adding a toolbar row.
        rows.relations(contentLeft + contentWidth - 36, y - 3, 36);
        int statusBottom = EditorTextRenderer.drawWrapped(graphics, font, statusText.getString(),
                contentLeft, y, Math.max(1, contentWidth - 68), model.statusColor());
        int statusTop = y;
        int statusWidth = Math.min(font.width(statusText), Math.max(1, contentWidth - 68));
        if (model.gameplay() && status == QuestStatus.LOCKED && !quest.behavior().hideLockIcon()) {
            // Cyan underline and info glyph advertise that the locked reason is inspectable.
            graphics.fill(contentLeft, y + font.lineHeight, contentLeft + statusWidth, y + font.lineHeight + 1, 0xFF68BDE8);
            graphics.drawString(font, Component.literal("ⓘ"), contentLeft + statusWidth + 4, y, 0xFF68BDE8, false);
            statusWidth += 4 + font.width("ⓘ");
        }
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
            int pinX = contentLeft + contentWidth - 10 - 42;
            QuestActionIcons.named("pin").render(graphics, font, new UiRect(pinX, y, pinX + 10, y + 10),
                    status == QuestStatus.ACTIVE ? 0xFF57C7F2 : GraystonePalette.SECONDARY);
            if (mouseX >= pinX - 2 && mouseX <= pinX + 10 + 2 && mouseY >= y && mouseY <= y + font.lineHeight) {
                hint = Component.translatable(status == QuestStatus.ACTIVE
                        ? "screen.brnquest.untrack" : "screen.brnquest.track");
            }
        }
        UiRect trackAction = null;
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
            int pinX = contentLeft + contentWidth - 10 - 42;
            trackAction = visiblePart(new UiRect(pinX - 2, y, pinX + 10 + 2,
                    y + font.lineHeight), layout.content());
        }
        UiRect completeAction = null;
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && ready) {
            Component readyText = Component.translatable("screen.brnquest.ready");
            int readyX = contentLeft;
            y = statusBottom + 5;
            int readyBottom = EditorTextRenderer.drawWrapped(graphics, font, readyText.getString(), readyX, y,
                    contentWidth, 0xFF72D88D);
            completeAction = visiblePart(new UiRect(readyX, y, readyX + Math.min(contentWidth, font.width(readyText)),
                    readyBottom), layout.content());
            y = readyBottom - font.lineHeight;
        }
        y = Math.max(y + font.lineHeight, statusBottom) + 8;

        if (editing || !model.description().isBlank()) {
            int descriptionTop = y;
            String description = model.description().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_description").getString()
                    : model.description();
            y = EditorTextRenderer.drawWrapped(graphics, font, description, contentLeft, y, contentWidth,
                    model.description().isBlank() ? GraystonePalette.DISABLED : GraystonePalette.TEXT);
            if (editing) addTextArea(textAreas, layout.content(), "DESCRIPTION",
                    contentLeft, descriptionTop, contentWidth, y);
            y += 8;
        }

        if (editing) {
            for (Map.Entry<String, UiRect> entry : textAreas.entrySet()) {
                if (entry.getValue().containsExclusive(mouseX, mouseY)) {
                    hint = Component.translatable(entry.getKey().equals("DESCRIPTION")
                            ? "screen.brnquest.editor.quick_edit.open_editor_hint"
                            : "screen.brnquest.editor.quick_edit.hint");
                    break;
                }
            }
        }

        y = renderSection(graphics, font, "screen.brnquest.requirements", "checkmark", contentLeft, y, contentWidth, 0xFFBFC7AC);
        if (quest.tasks().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.brnquest.no_requirements"), contentLeft, y, GraystonePalette.MUTED, false);
            y += 18;
        } else {
            for (TaskDefinition task : quest.tasks()) y = rows.task(task, contentLeft, y, contentWidth);
        }

        List<RewardDefinition> visibleRewards = quest.rewards().stream()
                .filter(reward -> reward.policy().visible() || editing || model.suppressAutoClaim()).toList();
        if (!visibleRewards.isEmpty()) {
            y += 4;
            y = renderSection(graphics, font, "screen.brnquest.rewards", "reward_table", contentLeft, y, contentWidth, 0xFFE6C77B);
            // Candidate buttons reserve their own horizontal space without enlarging every reward.
            int rewardX = contentLeft;
            for (RewardDefinition reward : visibleRewards) {
                int cellWidth = rows.rewardWidth(reward);
                if (rewardX > contentLeft && rewardX + cellWidth > contentLeft + contentWidth) {
                    rewardX = contentLeft; y += QuestViewportMath.REWARD_ROW_HEIGHT;
                }
                rows.reward(reward,rewardX,y);
                rewardX += cellWidth;
            }
            y += QuestViewportMath.REWARD_ROW_HEIGHT;
        }
        contentHeight = Math.max(0, y + (int) Math.round(drawnScroll) - layout.content().top() + 8);
        scroll.constrain(contentHeight, viewportHeight);
        graphics.disableScissor();


        boolean locked = model.gameplay() && status == QuestStatus.LOCKED && !quest.behavior().hideLockIcon()
                && mouseX >= contentLeft && mouseX <= contentLeft + statusWidth
                && mouseY >= statusTop && mouseY <= statusTop + font.lineHeight
                && statusTop >= layout.content().top() && statusTop < layout.content().bottom();
        return new Result(Map.copyOf(textAreas), completeAction, trackAction, hint, locked);
    }

    /** Compact section landmarks reuse existing sprites without introducing item interaction semantics. */
    private static int renderSection(GuiGraphics graphics, Font font, String title, String icon,
                                     int x, int y, int width, int color) {
        // A small icon and rule match property-form sections without adding another filled card.
        graphics.fill(x, y + 18, x + width, y + 19, GraystonePalette.LIP);
        (icon.equals("checkmark") ? TASK_SECTION_ICON : REWARD_SECTION_ICON)
                .render(graphics, font, new UiRect(x, y, x + 16, y + 16), color);
        EditorTextRenderer.drawFittedString(graphics, font, Component.translatable(title),
                x + 21, y + 4, Math.max(1, width - 23), color, 0.75F);
        return y + 22;
    }

    private static void addTextArea(Map<String, UiRect> areas, UiRect viewport, String key,
                                    int left, int top, int width, int bottom) {
        UiRect visible = new UiRect(left, top, left + width, bottom).intersection(viewport);
        if (visible.height() > 0 && visible.width() > 0) areas.put(key, visible);
    }

    private static UiRect visiblePart(UiRect bounds, UiRect viewport) {
        UiRect visible = bounds.intersection(viewport);
        return visible.width() > 0 && visible.height() > 0 ? visible : null;
    }
}

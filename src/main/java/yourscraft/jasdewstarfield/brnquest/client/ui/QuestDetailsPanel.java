package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.LinkedHashMap;
import java.util.Map;

/** Detail section composition. The caller supplies row renderers; this panel owns text flow and scrolling. */
final class QuestDetailsPanel {
    record Layout(UiRect content, UiRect clip, int trackX) {}
    record Model(QuestDefinition quest, String title, QuestStatus status, Component statusText,
                 int statusColor, boolean editing, boolean gameplay, boolean ready) {}
    interface Rows {
        int task(TaskDefinition task, int x, int y, int width);
        void reward(RewardDefinition reward, int x, int y);
    }
    record Result(Map<String, UiRect> textAreas, Component hint, boolean lockedStatusHovered) {}
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private int contentHeight;
    private double drawnScroll;
    private int statusY = Integer.MIN_VALUE;
    EditorSmoothScroll scroll() { return scroll; }
    int contentHeight() { return contentHeight; }
    int statusY() { return statusY; }
    void reset() { scroll.snap(0); contentHeight = 0; statusY = Integer.MIN_VALUE; }

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

        if (editing || !quest.subtitle().isBlank()) {
            int subtitleTop = y;
            String subtitle = quest.subtitle().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_subtitle").getString()
                    : quest.subtitle();
            y = EditorTextRenderer.drawWrapped(graphics, font, subtitle, contentLeft, y, contentWidth,
                    quest.subtitle().isBlank() ? 0xFF718096 : 0xFFB7C5D8);
            if (editing) addTextArea(textAreas, layout.content(), "SUBTITLE", contentLeft, subtitleTop, contentWidth, y);
            y += 4;
        }

        boolean ready = model.ready();
        Component statusText = model.statusText();
        graphics.drawString(font, statusText, contentLeft, y, model.statusColor(), false);
        int statusTop = y;
        int statusWidth = font.width(statusText);
        if (model.gameplay() && status == QuestStatus.LOCKED) {
            // Cyan underline and info glyph advertise that the locked reason is inspectable.
            graphics.fill(contentLeft, y + font.lineHeight, contentLeft + statusWidth, y + font.lineHeight + 1, 0xFF68BDE8);
            graphics.drawString(font, Component.literal("ⓘ"), contentLeft + statusWidth + 4, y, 0xFF68BDE8, false);
            statusWidth += 4 + font.width("ⓘ");
        }
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
            String pin = status == QuestStatus.ACTIVE ? "★" : "☆";
            int pinX = contentLeft + contentWidth - font.width(pin) - 18;
            graphics.drawString(font, Component.literal(pin), pinX, y, status == QuestStatus.ACTIVE ? 0xFF57C7F2 : 0xFFB7C5D8, false);
            if (mouseX >= pinX - 2 && mouseX <= pinX + font.width(pin) + 2 && mouseY >= y && mouseY <= y + font.lineHeight) {
                hint = Component.translatable(status == QuestStatus.ACTIVE
                        ? "screen.brnquest.untrack" : "screen.brnquest.track");
            }
        }
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && ready) {
            graphics.drawString(font, Component.translatable("screen.brnquest.ready"), contentLeft + font.width(statusText) + 6, y, 0xFF72D88D, false);
        }
        y += 16;

        if (editing || !quest.description().isBlank()) {
            int descriptionTop = y;
            String description = quest.description().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_description").getString()
                    : quest.description();
            y = EditorTextRenderer.drawWrapped(graphics, font, description, contentLeft, y, contentWidth,
                    quest.description().isBlank() ? 0xFF718096 : 0xFFE1E6EE);
            if (editing) addTextArea(textAreas, layout.content(), "DESCRIPTION",
                    contentLeft, descriptionTop, contentWidth, y);
            y += 8;
        }

        if (editing) {
            for (UiRect area : textAreas.values()) {
                if (area.containsExclusive(mouseX, mouseY)) {
                    hint = Component.translatable("screen.brnquest.editor.quick_edit.hint");
                    break;
                }
            }
        }

        graphics.drawString(font, Component.translatable("screen.brnquest.requirements"), contentLeft, y, 0xFF8FB8E2, false);
        y += 13;
        if (quest.tasks().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.brnquest.no_requirements"), contentLeft, y, 0xFF9AA6B5, false);
            y += 18;
        } else {
            for (TaskDefinition task : quest.tasks()) y = rows.task(task, contentLeft, y, contentWidth);
        }

        if (!quest.rewards().isEmpty()) {
            y += 4;
            graphics.drawString(font, Component.translatable("screen.brnquest.rewards"), contentLeft, y, 0xFFE6B55B, false);
            y += 13;
            int rewardTop = y;
            int rewardColumns = Math.max(1, contentWidth / QuestViewportMath.REWARD_ROW_HEIGHT);
            int column = 0;
            for (RewardDefinition reward : quest.rewards()) {
                int rewardX = contentLeft + column * QuestViewportMath.REWARD_ROW_HEIGHT;
                rows.reward(reward, rewardX, y);
                column++;
                if (column >= rewardColumns) {
                    column = 0;
                    y += QuestViewportMath.REWARD_ROW_HEIGHT;
                }
            }
            // Compute from row count so a partially populated final row is never clipped.
            y = rewardTop + QuestViewportMath.rewardGridHeight(quest.rewards().size(), rewardColumns);
        }
        contentHeight = Math.max(0, y + (int) Math.round(drawnScroll) - layout.content().top() + 8);
        scroll.constrain(contentHeight, viewportHeight);
        graphics.disableScissor();


        statusY = statusTop;
        boolean locked = model.gameplay() && status == QuestStatus.LOCKED
                && mouseX >= contentLeft && mouseX <= contentLeft + statusWidth
                && mouseY >= statusTop && mouseY <= statusTop + font.lineHeight
                && statusTop >= layout.content().top() && statusTop < layout.content().bottom();
        return new Result(Map.copyOf(textAreas), hint, locked);
    }

    private static void addTextArea(Map<String, UiRect> areas, UiRect viewport, String key,
                                    int left, int top, int width, int bottom) {
        UiRect visible = new UiRect(left, top, left + width, bottom).intersection(viewport);
        if (visible.height() > 0 && visible.width() > 0) areas.put(key, visible);
    }
}

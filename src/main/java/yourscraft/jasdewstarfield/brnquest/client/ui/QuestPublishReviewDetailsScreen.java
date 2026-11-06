package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import java.util.ArrayList;
import java.util.List;

/** Read-only review details wrap at the current GUI width, with every line reachable by scrolling. */
final class QuestPublishReviewDetailsScreen extends Screen {
    private final Screen parent;
    private final List<Component> source;
    private final EditorListPanel<Integer> list = new EditorListPanel<>();
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private EditorListNavigationWidget<Integer> navigation;
    private long lastFrame;

    QuestPublishReviewDetailsScreen(Screen parent, List<Component> source) {
        super(Component.translatable("screen.brnquest.editor.publish.review.details"));
        this.parent = parent;
        this.source = List.copyOf(source);
    }

    @Override protected void init() {
        list.invalidate(); lastFrame = 0;
        int left = left(), right = right();
        lines.clear();
        for (var field : source) {
            lines.addAll(font.split(field, Math.max(20, right - left - 16)));
            lines.add(FormattedCharSequence.EMPTY); // Keep adjacent field groups distinct after wrapping.
        }
        navigation = addRenderableWidget(new EditorListNavigationWidget<>(title, list, row -> {}));
        addRenderableWidget(new EditorButtonWidget(left, height - 30, right - left, 20,
                Component.translatable("gui.done"), button -> onClose()));
    }

    private int left() { return Math.max(12, width / 2 - 260); }
    private int right() { return Math.min(width - 12, left() + 520); }

    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        super.renderBackground(graphics, x, y, partial);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        GraystoneSurface.raised(graphics, new UiRect(left() - 4, 8, right() + 4, Math.max(8, height - 6)), GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.min(.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        var frame = list.advance(new UiRect(left(), 36, right() - 6, Math.max(36, height - 36)),
                new UiRect(0, 0, width, height), right() - 3, font.lineHeight + 2, 0,
                lines.size(), i -> i, elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        navigation.update(frame);
        list.render(graphics, row -> graphics.drawString(font, lines.get(row.key()),
                row.bounds().left() + 4, row.bounds().top(), 0xFFFFFFFF, false), () -> {});
        super.render(graphics, x, y, partial);
    }

    @Override public boolean mouseClicked(double x, double y, int button) { return list.mouseClicked(x, y, button) || super.mouseClicked(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return list.mouseDragged(y, button) || super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return list.mouseReleased(button) || super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { return list.mouseScrolled(x, y, dy, BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x, y, dx, dy); }
    // The editing lease must keep ticking while the user reads a long review.
    @Override public void tick() { parent.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

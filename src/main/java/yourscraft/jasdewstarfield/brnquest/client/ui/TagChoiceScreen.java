package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import java.util.List;
import java.util.function.Consumer;

/** Secondary authoring menu used when one item belongs to several item tags. */
public final class TagChoiceScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 178;
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private final ItemStack source;
    private final List<ResourceLocation> tags;
    private final Consumer<ResourceLocation> selectionConsumer;
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private long previousFrameNanos;
    private double renderedScroll;

    public TagChoiceScreen(Screen parent, ItemStack source, List<ResourceLocation> tags,
                           Consumer<ResourceLocation> selectionConsumer) {
        super(Component.translatable("screen.brnquest.tag_choice.title"));
        this.parent = parent;
        this.source = source.copyWithCount(1);
        this.tags = List.copyOf(tags);
        this.selectionConsumer = selectionConsumer;
    }

    @Override
    public void tick() {
        parent.tick();
        super.tick();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x70151820);
        UiRect panel = panel();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xF0202632);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 8, 0xFFFFFFFF);
        graphics.renderItem(source, panel.left() + 8, panel.top() + 24);
        Component hint = Component.translatable("screen.brnquest.tag_choice.hint", tags.size());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(hint.getString(), panel.width() - 38)),
                panel.left() + 31, panel.top() + 28, 0xFF9FB0C2, false);

        UiRect viewport = viewport();
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0 ? 1.0 / 60.0
                : Math.min(0.1, Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0));
        previousFrameNanos = now;
        renderedScroll = scroll.frameAndRender(graphics, viewport.right() + 2, viewport.top(), viewport.bottom(),
                tags.size() * ROW_HEIGHT, viewport.height(), elapsed,
                BrnQuestClientConfig.VALUES.smoothSpeed.get());

        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        for (int index = 0; index < tags.size(); index++) {
            UiRect row = row(index);
            if (row.bottom() <= viewport.top() || row.top() >= viewport.bottom()) continue;
            int background = row.contains(mouseX, mouseY) ? 0xFF56697C : 0xFF2A313C;
            graphics.fill(row.left(), row.top(), row.right(), row.bottom() - 1, background);
            String text = font.plainSubstrByWidth(tags.get(index).toString(), row.width() - 10);
            graphics.drawString(font, text, row.left() + 5, row.top() + 5, 0xFFFFFFFF, false);
        }
        graphics.disableScissor();

        EditorButton.renderInteractive(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && cancelBounds().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            selectionConsumer.accept(tags.get(index));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect viewport = viewport();
        if (viewport.contains(mouseX, mouseY)) {
            scroll.scrollWheel(scrollY, BrnQuestClientConfig.VALUES.scrollStep.get(),
                    tags.size() * ROW_HEIGHT, viewport.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private int rowAt(double mouseX, double mouseY) {
        UiRect viewport = viewport();
        if (!viewport.contains(mouseX, mouseY)) return -1;
        int index = (int) ((mouseY - viewport.top() + renderedScroll) / ROW_HEIGHT);
        return index >= 0 && index < tags.size() ? index : -1;
    }

    private UiRect row(int index) {
        UiRect viewport = viewport();
        int top = viewport.top() + index * ROW_HEIGHT - (int) Math.round(renderedScroll);
        return new UiRect(viewport.left(), top, viewport.right(), top + ROW_HEIGHT);
    }

    private UiRect panel() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(4, (height - PANEL_HEIGHT) / 2);
        return new UiRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
    }

    private UiRect viewport() {
        UiRect panel = panel();
        return new UiRect(panel.left() + 7, panel.top() + 47, panel.right() - 9, panel.bottom() - 29);
    }

    private UiRect cancelBounds() {
        UiRect panel = panel();
        return new UiRect(panel.left() + 7, panel.bottom() - 22, panel.right() - 7, panel.bottom() - 5);
    }
}

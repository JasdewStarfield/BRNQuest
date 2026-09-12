package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import java.util.List;
import java.util.function.Consumer;

/** Secondary authoring menu used when one item belongs to several item tags. */
public final class TagChoiceScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 178;
    private static final int ROW_HEIGHT = 18;

    /** Tab focuses actions; activation reuses the pointer route and never moves real inventory stacks. */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key != 258 && list.navigate(key, (modifiers & 1) != 0)) { buttonInput.clearFocus(); return true; }
        if (key == 258) list.clearFocus();
        if ((key == 257 || key == 335) && list.focusedRow().isPresent()) {
            selectionConsumer.accept(list.focusedRow().orElseThrow().key()); onClose(); return true;
        }
        if (buttonInput.keyPressed(key, (modifiers & 1) != 0, area -> {
            mouseClicked(area.centerX(), area.centerY(), 0);
            mouseReleased(area.centerX(), area.centerY(), 0);
        })) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    private final EditorButtonInput buttonInput = new EditorButtonInput();
    private final Screen parent;
    private final ItemStack source;
    private final List<ResourceLocation> tags;
    private final Consumer<ResourceLocation> selectionConsumer;
    private final EditorListPanel<ResourceLocation> list = new EditorListPanel<>();
    private long previousFrameNanos;

    public TagChoiceScreen(Screen parent, ItemStack source, List<ResourceLocation> tags,
                           Consumer<ResourceLocation> selectionConsumer) {
        super(Component.translatable("screen.brnquest.tag_choice.title"));
        this.parent = parent;
        this.source = source.copyWithCount(1);
        this.tags = List.copyOf(tags);
        this.selectionConsumer = selectionConsumer;
    }

    @Override
    protected void init() {
        buttonInput.begin(); buttonInput.clearFocus();
        super.init();
        // New geometry after resize must be drawn before it becomes interactive.
        list.invalidate();
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
        buttonInput.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        UiRect panel = panel();
        GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 8, 0xFFFFFFFF);
        graphics.renderItem(source, panel.left() + 8, panel.top() + 24);
        Component hint = Component.translatable("screen.brnquest.tag_choice.hint", tags.size());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(hint.getString(), panel.width() - 38)),
                panel.left() + 31, panel.top() + 28, GraystonePalette.SECONDARY, false);

        UiRect viewport = viewport();
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0 ? 1.0 / 60.0
                : Math.min(0.1, Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0));
        previousFrameNanos = now;
        list.advance(viewport, panel, viewport.right() + 2, ROW_HEIGHT, 1, tags.size(), tags::get,
                elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        list.render(graphics, row -> {
            UiRect rect = row.bounds();
            int background = row.visible().containsExclusive(mouseX, mouseY) ? 0xFF5C6056 : GraystonePalette.HOVER;
            GraystoneSurface.raised(graphics, rect, background, true);
            String text = font.plainSubstrByWidth(row.key().toString(), Math.max(0, rect.width() - 10));
            graphics.drawString(font, text, rect.left() + 5, rect.top() + 5, 0xFFFFFFFF, false);
        }, () -> {});

        buttonInput.render(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        // The visible tag may be shortened; its full identity stays readable above the completed panel.
        list.rowAt(mouseX, mouseY).ifPresent(row -> graphics.renderComponentTooltip(font,
                List.of(Component.literal(row.key().toString())), mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttonInput.clicked(mouseX, mouseY, button);
        if (button == 0 && cancelBounds().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (list.mouseClicked(mouseX, mouseY, button)) return true;
        var selected = list.rowAt(mouseX, mouseY);
        if (button == 0 && selected.isPresent()) {
            selectionConsumer.accept(selected.orElseThrow().key());
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.mouseScrolled(mouseX, mouseY, scrollY, BrnQuestClientConfig.VALUES.scrollStep.get())) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private UiRect panel() {
        int panelWidth = Math.max(0, Math.min(PANEL_WIDTH, width - 8));
        int panelHeight = Math.max(0, Math.min(PANEL_HEIGHT, height - 8));
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect viewport() {
        UiRect panel = panel();
        int bottom = Math.max(panel.top(), cancelBounds().top() - 7);
        return new UiRect(panel.left() + 7, Math.min(panel.top() + 47, bottom),
                Math.max(panel.left() + 7, panel.right() - 9), bottom);
    }

    private UiRect cancelBounds() {
        UiRect panel = panel();
        return new UiRect(panel.left() + 7, panel.bottom() - 22, panel.right() - 7, panel.bottom() - 5);
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.text.MinecraftTextColor;

import java.util.Locale;
import java.util.function.Consumer;

/** Selects one of Minecraft's legacy colors or an exact opaque RGB Markdown style. */
public final class EditorMarkdownColorScreen extends Screen {
    private static final MinecraftTextColor[] COLORS = MinecraftTextColor.values();

    private final Screen parent;
    private final Consumer<String> consumer;
    private EditBox rgb;
    private Component issue;

    public EditorMarkdownColorScreen(Screen parent, Consumer<String> consumer) {
        super(Component.translatable("screen.brnquest.editor.markdown.color.title"));
        this.parent = parent;
        this.consumer = consumer;
    }

    @Override
    protected void init() {
        UiRect panel = panelBounds();
        rgb = new EditBox(font, panel.left() + 12, panel.bottom() - 58, panel.width() - 24, 20,
                Component.translatable("screen.brnquest.editor.markdown.color.rgb"));
        rgb.setMaxLength(7);
        rgb.setHint(Component.literal("#RRGGBB"));
        addRenderableWidget(rgb);
        setFocused(rgb);
    }

    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        UiRect panel = panelBounds();
        GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 10, 0xFFFFFFFF);
        for (int index = 0; index < COLORS.length; index++) {
            UiRect cell = colorBounds(index);
            MinecraftTextColor entry = COLORS[index];
            graphics.fill(cell.left(), cell.top(), cell.right(), cell.bottom(), 0xFF000000 | entry.rgb());
            graphics.renderOutline(cell.left(), cell.top(), cell.width(), cell.height(),
                    cell.containsExclusive(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF858A7C);
        }
        drawButton(graphics, applyBounds(), Component.translatable("gui.done"), mouseX, mouseY);
        drawButton(graphics, cancelBounds(), Component.translatable("gui.cancel"), mouseX, mouseY);
        if (issue != null) graphics.drawCenteredString(font, issue, panel.centerX(), panel.bottom() - 70, 0xFFFF8B8B);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int index = 0; index < COLORS.length; index++) {
            if (colorBounds(index).containsExclusive(mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable(
                        "screen.brnquest.editor.markdown.color." + COLORS[index].serializedName()), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawButton(GuiGraphics graphics, UiRect bounds, Component label, int mouseX, int mouseY) {
        int color = bounds.containsExclusive(mouseX, mouseY) ? 0xFF777866 : 0xFF5D6258;
        GraystoneSurface.raised(graphics, bounds, color, true);
        graphics.drawCenteredString(font, label, bounds.centerX(), bounds.top() + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int index = 0; index < COLORS.length; index++) {
                if (colorBounds(index).containsExclusive(mouseX, mouseY)) {
                    choose(COLORS[index].serializedName());
                    return true;
                }
            }
            if (applyBounds().containsExclusive(mouseX, mouseY)) {
                String color = normalizeColor(rgb.getValue());
                if (color == null) issue = Component.translatable("screen.brnquest.editor.markdown.color.invalid");
                else choose(color);
                return true;
            }
            if (cancelBounds().containsExclusive(mouseX, mouseY)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(String color) {
        consumer.accept(color);
        onClose();
    }

    static String normalizeColor(String raw) {
        if (raw == null) return null;
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) value = value.substring(1);
        if (value.matches("[0-9a-f]{6}")) return value;
        if (MinecraftTextColor.byName(value) != null) return value;
        return null;
    }

    private UiRect panelBounds() {
        int panelWidth = Math.min(360, Math.max(220, width - 32));
        int panelHeight = Math.min(260, Math.max(220, height - 32));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect colorBounds(int index) {
        UiRect panel = panelBounds();
        int gap = 6;
        int columns = 4;
        int cellWidth = (panel.width() - 24 - gap * (columns - 1)) / columns;
        int left = panel.left() + 12 + (index % columns) * (cellWidth + gap);
        int top = panel.top() + 32 + (index / columns) * 25;
        return new UiRect(left, top, left + cellWidth, top + 19);
    }

    private UiRect cancelBounds() { UiRect p = panelBounds(); return new UiRect(p.left()+12,p.bottom()-30,p.centerX()-3,p.bottom()-8); }
    private UiRect applyBounds() { UiRect p = panelBounds(); return new UiRect(p.centerX()+3,p.bottom()-30,p.right()-12,p.bottom()-8); }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

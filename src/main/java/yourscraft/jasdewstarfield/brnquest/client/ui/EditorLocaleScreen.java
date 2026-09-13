package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import java.util.List;
import java.util.function.Consumer;

/** Small language chooser: existing codes are selectable, new codes do not change the player's language. */
final class EditorLocaleScreen extends Screen {
    private final Screen parent;
    private final List<String> locales;
    private final Consumer<String> selection;
    private String code;
    private EditBox input;

    EditorLocaleScreen(Screen parent, List<String> locales, String current, Consumer<String> selection) {
        super(Component.translatable("screen.brnquest.editor.locale.choose"));
        this.parent = parent;
        this.locales = List.copyOf(locales);
        this.code = current;
        this.selection = selection;
    }

    @Override protected void init() {
        int left = width / 2 - 130, top = height / 2 - 65;
        addRenderableWidget(new EditorButtonWidget(left, top + 28, 260, 20,
                Component.translatable("screen.brnquest.editor.locale.existing"), button -> {
                    // The generic searchable chooser is already shared with the other property selectors.
                    var choices = locales.stream().map(locale -> new EditorChoiceScreen.Choice(locale, Component.literal(locale))).toList();
                    minecraft.setScreen(new EditorChoiceScreen(this, title, choices, code, value -> code = value));
                }));
        input = new EditBox(font, left, top + 56, 260, 20, title);
        input.setMaxLength(16);
        input.setValue(code);
        input.setHint(Component.translatable("screen.brnquest.editor.locale.code"));
        var done = new EditorButtonWidget(left + 134, top + 92, 126, 20,
                Component.translatable("gui.done"), button -> apply());
        input.setResponder(value -> { code = value; done.active = EditorLocalizedText.validLocale(code); });
        done.active = EditorLocalizedText.validLocale(code);
        addRenderableWidget(input);
        addRenderableWidget(new EditorButtonWidget(left, top + 92, 126, 20,
                Component.translatable("gui.cancel"), button -> onClose()));
        addRenderableWidget(done);
        setInitialFocus(input);
    }

    private void apply() {
        if (!EditorLocalizedText.validLocale(code)) return;
        selection.accept(code.strip());
        onClose();
    }

    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 750);
        try {
            graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
            GraystoneSurface.raised(graphics, new UiRect(width / 2 - 142, height / 2 - 73,
                    width / 2 + 142, height / 2 + 61), GraystonePalette.PANEL, true);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 59, 0xFFFFFFFF);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.locale.code"),
                    width / 2 - 130, height / 2 + 14, GraystonePalette.SECONDARY, false);
            super.render(graphics, x, y, partial);
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            apply(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

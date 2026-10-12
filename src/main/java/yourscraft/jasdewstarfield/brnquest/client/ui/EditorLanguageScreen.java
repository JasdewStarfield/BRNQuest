package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import java.util.function.Consumer;

/** Low-frequency locale creation stays outside the writing viewport and returns to the preserved draft. */
final class EditorLanguageScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> consumer;
    private EditBox locale;
    EditorLanguageScreen(Screen parent, Consumer<String> consumer) {
        super(Component.translatable("screen.brnquest.editor.text.language_add"));
        this.parent=parent; this.consumer=consumer;
    }
    @Override protected void init() {
        int w=Math.min(320,Math.max(80,width-24)), left=(width-w)/2, top=Math.max(30,height/2-20);
        locale=addRenderableWidget(new EditBox(font,left,top,w,20,title));
        locale.setMaxLength(32);
        locale.setHint(Component.translatable("screen.brnquest.editor.localized_text.new_locale_hint"));
        addRenderableWidget(new EditorButtonWidget(left,top+28,w/2-3,20,Component.translatable("gui.cancel"), b -> onClose()));
        addRenderableWidget(new EditorButtonWidget(left+w/2+3,top+28,w/2-3,20,Component.translatable("gui.done"), b -> confirm()));
        setFocused(locale);
    }
    private void confirm() {
        String value=locale.getValue().strip();
        if (!EditorLocalizedQuestTextScreen.validLocaleCode(value)) { locale.setTextColor(0xFFFF8B8B); return; }
        consumer.accept(value); onClose();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if ((key==257||key==335)&&getFocused()==locale) { confirm(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
        ChildScreenBackground.render(parent,graphics,width,height,partial);
        graphics.fill(0,0,width,height,GraystonePalette.BACKDROP);
        var panel=new UiRect(locale.getX()-8,locale.getY()-28,locale.getX()+locale.getWidth()+8,locale.getY()+58);
        GraystoneSurface.raised(graphics,panel,GraystonePalette.PANEL,true);
        graphics.drawCenteredString(font,title,width/2,locale.getY()-19,GraystonePalette.TEXT);
        super.render(graphics,x,y,partial);
    }
    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

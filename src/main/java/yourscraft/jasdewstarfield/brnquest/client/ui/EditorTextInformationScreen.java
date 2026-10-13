package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import java.util.function.BiConsumer;

/** Short windows edit metadata in a compact child rather than losing the title/subtitle entry points. */
final class EditorTextInformationScreen extends Screen {
    private final Screen parent;
    private final BiConsumer<String,String> consumer;
    private final String initialTitle, initialSubtitle;
    private EditorTextField titleField, subtitleField;
    private UiRect panel;
    EditorTextInformationScreen(Screen parent,String title,String subtitle,BiConsumer<String,String> consumer) {
        super(Component.translatable("screen.brnquest.editor.text.information"));
        this.parent=parent; this.initialTitle=title; this.initialSubtitle=subtitle; this.consumer=consumer;
    }
    @Override protected void init() {
        String draftTitle=titleField==null?initialTitle:titleField.getValue();
        String draftSubtitle=subtitleField==null?initialSubtitle:subtitleField.getValue();
        int w=Math.min(480,Math.max(80,width-24)), h=Math.min(148,Math.max(80,height-16));
        panel=new UiRect((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);
        titleField=field("screen.brnquest.editor.quest.title",panel.top()+40,draftTitle);
        subtitleField=field("screen.brnquest.editor.quest.subtitle",panel.top()+78,draftSubtitle);
        addRenderableWidget(new EditorButtonWidget(panel.left()+10,panel.bottom()-30,w/2-14,20,
                Component.translatable("gui.cancel"),b->onClose()));
        addRenderableWidget(new EditorButtonWidget(panel.centerX()+4,panel.bottom()-30,w/2-14,20,
                Component.translatable("gui.done"),b->{ consumer.accept(titleField.getValue(),subtitleField.getValue()); onClose(); }));
        setFocused(titleField);
    }
    private EditorTextField field(String key,int top,String value) {
        var field=new EditorTextField(font,Component.translatable(key),256);
        field.setNativeFormatting(true); field.show(new UiRect(panel.left()+10,top,panel.right()-10,top+20),true);
        field.setValue(value); return addRenderableWidget(field);
    }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
        ChildScreenBackground.render(parent,graphics,width,height,partial);
        graphics.fill(0,0,width,height,GraystonePalette.BACKDROP);
        GraystoneSurface.raised(graphics,panel,GraystonePalette.PANEL,true);
        graphics.drawCenteredString(font,title,panel.centerX(),panel.top()+9,GraystonePalette.TEXT);
        graphics.drawString(font,titleField.getMessage(),panel.left()+10,panel.top()+28,GraystonePalette.SECONDARY,false);
        graphics.drawString(font,subtitleField.getMessage(),panel.left()+10,panel.top()+66,GraystonePalette.SECONDARY,false);
        super.render(graphics,x,y,partial);
    }
    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}

package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owned single-line editor; literal glyphs, selection and history never depend on EditBox internals. */
public final class EditorTextField extends AbstractWidget implements EditorTextInput {
    private final Font font;
    private final TextEditBuffer buffer;
    private EditorTextGeometry geometry;
    private long geometryRevision=-1;
    private double horizontalScroll;
    private boolean editable=true, bordered=true, dragging;
    private int textColor=0xFFFFFFFF, disabledColor=GraystonePalette.SECONDARY;
    private Component hint;
    private String suggestion;
    private long focusedAt;

    public EditorTextField(Font font,Component narration,int maximumLength) {
        super(0,0,10,18,narration); this.font=font;
        buffer=new TextEditBuffer(false,maximumLength); hide();
    }
    @Override public ImeGeometry imeGeometry() {
        ensureCaret();
        return new ImeGeometry(getX(),getY(),getWidth(),getHeight(),
                Math.clamp(padding()+(int)Math.round(geometry().distance(0,cursor())-horizontalScroll),0,getWidth()),
                (getHeight()-font.lineHeight)/2,font.lineHeight);
    }
    @Override public TextEditBuffer buffer() { return buffer; }
    @Override public boolean acceptsText() { return visible && active && editable && isFocused(); }
    public void setNativeFormatting(boolean value) { buffer.nativeCodes(value); }
    public String getValue() { return buffer.value(); }
    public void setValue(String value) { buffer.load(value); ensureCaret(); }
    public void setResponder(Consumer<String> callback) { buffer.listener(callback); }
    public void setFilter(Predicate<String> filter) { buffer.validator(filter); }
    public void setMaxLength(int limit) { buffer.limit(limit); }
    public void setHint(Component value) { hint=value; }
    public void setSuggestion(String value) { suggestion=value; }
    public void setEditable(boolean value) { editable=value; }
    public boolean isEditable() { return editable; }
    public void setBordered(boolean value) { bordered=value; }
    public void setTextColor(int value) { textColor=value; }
    public void setTextColorUneditable(int value) { disabledColor=value; }
    public int getCursorPosition() { return cursor(); }
    public int getAnchorPosition() { return anchor(); }
    public String getHighlighted() { return buffer.selectedText(); }
    public void setCursorPosition(int value) { buffer.move(value,false); ensureCaret(); }
    public void setHighlightPos(int value) { buffer.selection(cursor(),value); ensureCaret(); }
    public void setSelection(int cursor,int anchor) { buffer.selection(cursor,anchor); ensureCaret(); }
    public void insertText(String value) { buffer.insert(value,TextEditBuffer.Kind.PASTE,Util.getMillis()); ensureCaret(); }
    public void applyEdit(String value,int cursor,int anchor) {
        buffer.edit(value,cursor,anchor,TextEditBuffer.Kind.FORMAT,Util.getMillis()); ensureCaret();
    }
    public void show(UiRect bounds,boolean enabled) {
        setX(bounds.left()); setY(bounds.top()); setWidth(bounds.width()); setHeight(bounds.height());
        visible=true; active=enabled; ensureCaret();
    }
    public void setVisible(boolean value) { visible=value; }
    public void hide() { visible=false; active=false; }
    public void offsetForDrawerAnimation(int offsetX) { if(visible && offsetX!=0) { setX(getX()+offsetX); active=false; } }
    private int padding() { return bordered?5:0; }
    private int innerWidth() { return Math.max(1,getWidth()-padding()*2); }
    private EditorTextGeometry geometry() {
        if(geometryRevision!=buffer.revision()) {
            geometry=new EditorTextGeometry(getValue(),Integer.MAX_VALUE,
                    cp->font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp,Style.EMPTY)));
            geometryRevision=buffer.revision();
        }
        return geometry;
    }
    private void ensureCaret() {
        double x=geometry().distance(0,cursor());
        if(x<horizontalScroll) horizontalScroll=x;
        if(x>horizontalScroll+innerWidth()-2) horizontalScroll=x-innerWidth()+2;
        horizontalScroll=Math.clamp(horizontalScroll,0,Math.max(0,geometry().distance(0,getValue().length())-innerWidth()+2));
    }
    @Override public void setFocused(boolean focused) {
        if(focused && !isFocused()) focusedAt=Util.getMillis();
        super.setFocused(focused);
        if(!focused) { dragging=false; buffer.clearPendingCharacter(); }
    }
    @Override public void playDownSound(SoundManager manager) {} // Clicking text has no button activation sound.
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(!visible || !active || button!=0 || !isMouseOver(x,y)) return false;
        setFocused(true); dragging=true;
        buffer.move(geometry().hit(0,x-getX()-padding()+horizontalScroll),Screen.hasShiftDown());
        ensureCaret(); return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(!dragging || button!=0 || !acceptsText()) return false;
        buffer.move(geometry().hit(0,x-getX()-padding()+horizontalScroll),true); ensureCaret(); return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        boolean handled=dragging && button==0; if(button==0) dragging=false; return handled;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(!acceptsText()) return false;
        boolean handled=EditorTextInputKeys.key(buffer,key,0,getValue().length(),false);
        if(handled) { focusedAt=Util.getMillis(); ensureCaret(); } return handled;
    }
    @Override public boolean charTyped(char character,int modifiers) {
        if(!acceptsText()) return false;
        buffer.type(character,Util.getMillis()); focusedAt=Util.getMillis(); ensureCaret(); return true;
    }
    @Override protected void renderWidget(GuiGraphics graphics,int x,int y,float partial) {
        ensureCaret();
        if(bordered) {
            graphics.fill(getX(),getY(),getRight(),getBottom(),GraystonePalette.INSET);
            graphics.renderOutline(getX(),getY(),getWidth(),getHeight(),isFocused()?GraystonePalette.ACCENT:GraystonePalette.EDGE);
        }
        int left=getX()+padding(), top=getY()+(getHeight()-font.lineHeight)/2;
        graphics.enableScissor(left,getY()+1,Math.max(left,getRight()-padding()),getBottom()-1);
        int origin=left-(int)Math.round(horizontalScroll);
        if(buffer.selected()) {
            int from=origin+(int)Math.round(geometry().distance(0,buffer.start()));
            int to=origin+(int)Math.round(geometry().distance(0,buffer.end()));
            graphics.fill(from,top-1,to,top+font.lineHeight+1,0xAA446A8C);
        }
        if(getValue().isEmpty()) {
            Component placeholder=hint!=null?hint:suggestion==null?Component.empty():Component.literal(suggestion);
            graphics.drawString(font,placeholder,left,top,GraystonePalette.MUTED,false);
        } else graphics.drawString(font,FormattedCharSequence.forward(getValue(),Style.EMPTY),origin,top,editable?textColor:disabledColor,false);
        if(acceptsText() && (Util.getMillis()-focusedAt)/500%2==0) {
            int caret=origin+(int)Math.round(geometry().distance(0,cursor()));
            graphics.fill(caret,top-1,caret+1,top+font.lineHeight+1,GraystonePalette.TEXT);
        }
        graphics.disableScissor();
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE,Component.translatable("gui.narrate.editBox",getMessage(),getValue()));
    }
}

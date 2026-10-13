package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.function.Consumer;

/** Owned multiline source input. Wrapping, picking and caret movement share literal glyph coordinates. */
public final class EditorSourceField extends AbstractScrollWidget implements EditorTextInput {
    private final Font font;
    private final TextEditBuffer buffer=new TextEditBuffer(true,32_768);
    private EditorTextGeometry geometry;
    private long geometryRevision=-1, focusedAt;
    private int geometryWidth=-1;
    private boolean selecting;
    // Keep the current visual row alongside its logical offset at an ambiguous soft-wrap boundary.
    private int explicitCaretRow=-1, explicitCaretOffset=-1;
    private long explicitCaretRevision=-1;

    public EditorSourceField(Font font,UiRect bounds,Component narration) {
        super(bounds.left(),bounds.top(),bounds.width(),bounds.height(),narration);
        this.font=font; buffer.nativeCodes(true);
    }
    @Override public ImeGeometry imeGeometry() {
        int row=caretRow();
        return new ImeGeometry(getX(),getY(),getWidth(),getHeight(),
                innerPadding()+(int)Math.round(geometry().distance(geometry().lines().get(row).start(),cursor())),
                Math.clamp(innerPadding()+row*rowHeight()-(int)Math.round(scrollAmount()),0,Math.max(0,getHeight()-font.lineHeight)),font.lineHeight);
    }
    @Override public TextEditBuffer buffer() { return buffer; }
    @Override public boolean acceptsText() { return visible && active && isFocused(); }
    public String getValue() { return buffer.value(); }
    public void setValue(String value) { buffer.load(value); clearCaretRow(); revealCaret(); }
    public void setCharacterLimit(int value) { buffer.limit(value); }
    public void setValueListener(Consumer<String> listener) { buffer.listener(listener); }
    public void applyEdit(String value,int caret,int anchor) {
        buffer.edit(value,caret,anchor,TextEditBuffer.Kind.FORMAT,Util.getMillis()); clearCaretRow(); revealCaret();
    }
    public double savedScroll() { return scrollAmount(); }
    public void restoreScroll(double value) { setScrollAmount(value); }
    public void setSelection(int caret,int anchor) { buffer.selection(caret,anchor); clearCaretRow(); revealCaret(); }
    public void restoreState(TextEditBuffer.State state) { buffer.restore(state); clearCaretRow(); revealCaret(); }
    private EditorTextGeometry geometry() {
        int width=Math.max(1,getWidth()-totalInnerPadding());
        if(geometryRevision!=buffer.revision() || geometryWidth!=width) {
            geometry=new EditorTextGeometry(getValue(),width,
                    cp->font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp,Style.EMPTY)));
            geometryRevision=buffer.revision(); geometryWidth=width; clearCaretRow();
        }
        return geometry;
    }
    private int rowHeight() { return font.lineHeight+2; }
    private void revealCaret() {
        int top=innerPadding()+caretRow()*rowHeight();
        if(top<scrollAmount()+innerPadding()) setScrollAmount(top-innerPadding());
        if(top+rowHeight()>scrollAmount()+getHeight()-innerPadding())
            setScrollAmount(top+rowHeight()-getHeight()+innerPadding());
    }
    private void clearCaretRow() { explicitCaretRow=-1; explicitCaretOffset=-1; explicitCaretRevision=-1; }
    private int caretRow() {
        var layout=geometry();
        int preferred=explicitCaretOffset==cursor() && explicitCaretRevision==buffer.revision()?explicitCaretRow:-1;
        return layout.row(cursor(),preferred);
    }
    private void moveOnRow(int row,double x,boolean extend) {
        var layout=geometry(); int target=Math.clamp(row,0,layout.lines().size()-1);
        buffer.move(layout.hit(target,x),extend);
        explicitCaretRow=target; explicitCaretOffset=cursor(); explicitCaretRevision=buffer.revision();
        revealCaret();
    }
    private void moveToPoint(double x,double y,boolean extend) {
        int row=(int)Math.floor((y-getY()-innerPadding()+scrollAmount())/rowHeight());
        moveOnRow(row,x-getX()-innerPadding(),extend);
    }
    /** Each press starts at the displayed caret. A short row clamps to its end without reviving an earlier column. */
    void moveVertical(int delta,boolean extend) {
        int row=caretRow();
        double x=geometry().distance(geometry().lines().get(row).start(),cursor());
        moveOnRow(row+delta,x,extend);
    }
    @Override protected int getInnerHeight() { return geometry().lines().size()*rowHeight()+innerPadding(); }
    @Override protected double scrollRate() { return rowHeight()*3; }
    @Override public void setFocused(boolean focused) {
        if(focused && !isFocused()) focusedAt=Util.getMillis();
        super.setFocused(focused);
        if(!focused) { selecting=false; buffer.clearPendingCharacter(); }
    }
    @Override public void playDownSound(SoundManager manager) {} // Text selection is silent.
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(!visible || !active || button!=0 || !super.mouseClicked(x,y,button)) return false;
        setFocused(true);
        if(withinContentAreaPoint(x,y)) {
            selecting=true; moveToPoint(x,y,Screen.hasShiftDown());
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(selecting && button==0 && acceptsText()) {
            // Dragging beyond either edge scrolls the selection instead of pinning it to the visible last row.
            if(y<getY()+innerPadding()) setScrollAmount(scrollAmount()-rowHeight());
            else if(y>getBottom()-innerPadding()) setScrollAmount(scrollAmount()+rowHeight());
            moveToPoint(x,y,true); return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        boolean handled=selecting && button==0;
        if(button==0) selecting=false;
        return super.mouseReleased(x,y,button) || handled;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        return visible && active && isMouseOver(x,y) && super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(!acceptsText()) return false;
        boolean handled;
        if(key==265 || key==264 || key==266 || key==267) {
            buffer.clearPendingCharacter();
            int delta=key==265?-1:key==264?1:(key==266?-1:1)*Math.max(1,(getHeight()-totalInnerPadding())/rowHeight());
            moveVertical(delta,Screen.hasShiftDown()); handled=true;
        } else {
            int row=caretRow(); var line=geometry().lines().get(row);
            handled=EditorTextInputKeys.key(buffer,key,line.start(),line.end(),true);
            if(handled) {
                clearCaretRow();
                // Home/End address the displayed row even when its end is the next row's logical start.
                if((key==268 || key==269) && !Screen.hasControlDown()) {
                    explicitCaretRow=row; explicitCaretOffset=cursor(); explicitCaretRevision=buffer.revision();
                }
            }
        }
        if(handled) { revealCaret(); focusedAt=Util.getMillis(); } return handled;
    }
    @Override public boolean charTyped(char character,int modifiers) {
        if(!acceptsText()) return false;
        if(yourscraft.jasdewstarfield.brnquest.compat.imblocker.ImBlockerInputBridge.characterProbe(this,character)) return true;
        buffer.type(character,Util.getMillis()); clearCaretRow();
        revealCaret(); focusedAt=Util.getMillis(); return true;
    }
    @Override protected void renderBackground(GuiGraphics graphics) {
        graphics.fill(getX(),getY(),getRight(),getBottom(),GraystonePalette.INSET);
        graphics.renderOutline(getX(),getY(),getWidth(),getHeight(),isFocused()?GraystonePalette.ACCENT:GraystonePalette.EDGE);
    }
    @Override protected void renderContents(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        var rows=geometry().lines(); String text=getValue();
        int left=getX()+innerPadding(), top=getY()+innerPadding(), caretRow=caretRow();
        // Layout is cached on edits/width changes; rendering only touches rows intersecting the viewport.
        int first=Math.max(0,(int)(scrollAmount()/rowHeight())-1);
        int last=Math.min(rows.size(),first+getHeight()/rowHeight()+3);
        for(int index=first;index<last;index++) {
            var line=rows.get(index); int y=top+index*rowHeight();
            if(buffer.end()>line.start() && buffer.start()<=line.end() && buffer.selected()) {
                int from=left+(int)Math.round(geometry().distance(line.start(),Math.max(line.start(),buffer.start())));
                int to=buffer.end()>line.end()?getRight()-innerPadding()
                        :left+(int)Math.round(geometry().distance(line.start(),Math.min(line.end(),buffer.end())));
                graphics.fill(from,y-1,to,y+font.lineHeight+1,0xAA446A8C);
            }
            graphics.drawString(font,FormattedCharSequence.forward(text.substring(line.start(),line.end()),Style.EMPTY),left,y,GraystonePalette.TEXT,false);
            if(acceptsText() && index==caretRow && (Util.getMillis()-focusedAt)/500%2==0) {
                int x=left+(int)Math.round(geometry().distance(line.start(),cursor()));
                graphics.fill(x,y-1,x+1,y+font.lineHeight+1,GraystonePalette.TEXT);
            }
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE,Component.translatable("gui.narrate.editBox",getMessage(),getValue()));
    }
}

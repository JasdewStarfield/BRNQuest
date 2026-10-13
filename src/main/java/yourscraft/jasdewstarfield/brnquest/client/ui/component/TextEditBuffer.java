package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owned editing state: loading drafts is separate from user edits, which have bounded undo history. */
public final class TextEditBuffer {
    public enum Kind { TYPE, DELETE, PASTE, FORMAT }
    public record Selection(String text, int cursor, int anchor) {}
    public record State(Selection selection, List<Selection> undo, List<Selection> redo) {}
    private static final int HISTORY_STEPS=64, HISTORY_CHARACTERS=262_144;
    private final boolean multiline;
    private String text="";
    private int cursor, anchor, limit;
    private boolean nativeCodes;
    private long revision, lastEditTime=Long.MIN_VALUE;
    private Kind lastKind;
    private char pendingSurrogate;
    private Consumer<String> changed=value->{};
    private Predicate<String> validator=value->true;
    private final ArrayDeque<Selection> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();

    public TextEditBuffer(boolean multiline,int limit) { this.multiline=multiline; this.limit=Math.max(0,limit); }
    public String value() { return text; }
    public int cursor() { return cursor; }
    public int anchor() { return anchor; }
    public int start() { return Math.min(cursor,anchor); }
    public int end() { return Math.max(cursor,anchor); }
    public int limit() { return limit; }
    public long revision() { return revision; }
    public boolean selected() { return cursor!=anchor; }
    public String selectedText() { return text.substring(start(),end()); }
    public void nativeCodes(boolean enabled) { nativeCodes=enabled; }
    public void listener(Consumer<String> listener) { changed=Objects.requireNonNull(listener); }
    public void validator(Predicate<String> predicate) { validator=Objects.requireNonNull(predicate); }
    public void limit(int value) { limit=Math.max(0,value); if(text.length()>limit) load(text); }

    /** Programmatic loading never becomes a user undo step; identical rebinds preserve the history. */
    public void load(String value) {
        clearPendingCharacter();
        String next=truncate(Objects.requireNonNullElse(value,""),limit);
        if(next.equals(text) || !validator.test(next)) return;
        text=next; cursor=anchor=text.length(); undo.clear(); redo.clear(); breakGroup();
        revision++; changed.accept(text);
    }
    public void selection(int caret,int mark) { cursor=boundary(text,caret); anchor=boundary(text,mark); breakGroup(); }
    public void move(int position,boolean selecting) {
        cursor=boundary(text,position); if(!selecting) anchor=cursor; breakGroup();
    }
    public void horizontal(int direction,boolean selecting,boolean word) {
        if(!selecting && selected() && !word) { move(direction<0?start():end(),false); return; }
        move(word?wordBoundary(direction):step(cursor,direction),selecting);
    }
    public int wordBoundary(int direction) {
        int offset=cursor;
        if(direction<0) {
            while(offset>0 && Character.isWhitespace(text.codePointBefore(offset))) offset=step(offset,-1);
            while(offset>0 && !Character.isWhitespace(text.codePointBefore(offset))) offset=step(offset,-1);
        } else {
            while(offset<text.length() && !Character.isWhitespace(text.codePointAt(offset))) offset=step(offset,1);
            while(offset<text.length() && Character.isWhitespace(text.codePointAt(offset))) offset=step(offset,1);
        }
        return offset;
    }
    private int step(int offset,int direction) {
        return direction<0 ? offset>0?text.offsetByCodePoints(offset,-1):0
                : offset<text.length()?text.offsetByCodePoints(offset,1):text.length();
    }
    public boolean insert(String incoming,Kind kind,long now) {
        String clean=filter(incoming);
        if(incoming!=null && !incoming.isEmpty() && clean.isEmpty()) return false;
        int room=Math.max(0,limit-text.length()+end()-start());
        String inserted=truncate(clean,room);
        // A full field ignores a new character instead of replacing its selected text with nothing.
        if(!clean.isEmpty() && inserted.isEmpty()) return false;
        String result=text.substring(0,start())+inserted+text.substring(end());
        return edit(result,start()+inserted.length(),start()+inserted.length(),kind,now);
    }
    public boolean delete(int direction,boolean word,long now) {
        var before=new Selection(text,cursor,anchor);
        int from=selected()?start():Math.min(cursor,word?wordBoundary(direction):step(cursor,direction));
        int to=selected()?end():Math.max(cursor,word?wordBoundary(direction):step(cursor,direction));
        return apply(text.substring(0,from)+text.substring(to),from,from,Kind.DELETE,now,before);
    }
    /** Formatting commands replace the complete source atomically and retain their resulting selection. */
    public boolean edit(String value,int caret,int mark,Kind kind,long now) {
        return apply(value,caret,mark,kind,now,new Selection(text,cursor,anchor));
    }
    private boolean apply(String value,int caret,int mark,Kind kind,long now,Selection before) {
        clearPendingCharacter();
        if(value.length()>limit || !validator.test(value)) return false;
        if(value.equals(text)) { selection(caret,mark); return false; }
        boolean grouped=(kind==Kind.TYPE || kind==Kind.DELETE) && kind==lastKind && before.cursor()==before.anchor()
                && now>=lastEditTime && now-lastEditTime<=750;
        if(!grouped) { undo.addLast(before); trim(undo); }
        redo.clear(); text=value; cursor=boundary(text,caret); anchor=boundary(text,mark);
        lastKind=kind; lastEditTime=now; revision++; changed.accept(text); return true;
    }
    public boolean undo() { return history(undo,redo); }
    public boolean redo() { return history(redo,undo); }
    /** Minecraft delivers UTF-16 chars; join a committed supplementary character before filtering or editing. */
    public void type(char character,long now) {
        if(Character.isHighSurrogate(character)) { pendingSurrogate=character; return; }
        String inserted=Character.isLowSurrogate(character) && pendingSurrogate!=0
                ? new String(new char[]{pendingSurrogate,character}) : String.valueOf(character);
        pendingSurrogate=0;
        insert(inserted,Kind.TYPE,now);
    }
    public void clearPendingCharacter() { pendingSurrogate=0; }
    private boolean history(ArrayDeque<Selection> from,ArrayDeque<Selection> to) {
        if(from.isEmpty()) return false;
        to.addLast(new Selection(text,cursor,anchor)); trim(to);
        var next=from.removeLast(); text=next.text(); cursor=next.cursor(); anchor=next.anchor();
        breakGroup(); revision++; changed.accept(text); return true;
    }
    public State snapshot() { return new State(new Selection(text,cursor,anchor),List.copyOf(undo),List.copyOf(redo)); }
    public void restore(State state) {
        var next=state.selection(); load(next.text()); selection(next.cursor(),next.anchor());
        undo.clear(); redo.clear();
        // Never restore history for a snapshot whose text was rejected or truncated by this field's contract.
        if(!text.equals(next.text())) return;
        undo.addAll(state.undo()); redo.addAll(state.redo()); trim(undo); trim(redo);
    }
    private void breakGroup() { lastKind=null; lastEditTime=Long.MIN_VALUE; }
    private static void trim(ArrayDeque<Selection> history) {
        long chars=history.stream().mapToLong(entry->entry.text().length()).sum();
        while(history.size()>HISTORY_STEPS || chars>HISTORY_CHARACTERS)
            chars-=history.removeFirst().text().length();
    }
    private String filter(String value) {
        String normalized=Objects.requireNonNullElse(value,"").replace("\r\n","\n").replace('\r','\n');
        StringBuilder clean=new StringBuilder();
        normalized.codePoints().filter(cp->(cp>=32 && cp!=127 && (nativeCodes || cp!='§')
                && (cp<0xD800 || cp>0xDFFF)) || multiline && cp=='\n').forEach(clean::appendCodePoint);
        return clean.toString();
    }
    public static int boundary(String value,int index) {
        int safe=Math.clamp(index,0,value.length());
        if(safe>0 && safe<value.length() && Character.isHighSurrogate(value.charAt(safe-1))
                && Character.isLowSurrogate(value.charAt(safe))) safe--;
        return safe;
    }
    private static String truncate(String value,int length) { return value.substring(0,boundary(value,Math.min(value.length(),length))); }
}

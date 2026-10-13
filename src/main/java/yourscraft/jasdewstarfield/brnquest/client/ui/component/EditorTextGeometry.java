package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/** Linear-time literal layout with shared caret/hit coordinates; fractional glyph advances are never rounded per glyph. */
public final class EditorTextGeometry {
    private final String text;
    private final double[] advances;
    private final List<RawTextLayout.Line> lines;
    public EditorTextGeometry(String text,int width,IntToDoubleFunction glyphWidth) {
        this.text=text; advances=new double[text.length()+1];
        for(int index=0;index<text.length();) {
            int cp=text.codePointAt(index), next=index+Character.charCount(cp);
            advances[next]=advances[index]+(cp=='\n'?0:Math.max(0,glyphWidth.applyAsDouble(cp))); index=next;
        }
        var rows=new ArrayList<RawTextLayout.Line>();
        int start=0,cursor=0,preferred=-1;
        while(cursor<text.length()) {
            if(text.charAt(cursor)=='\n') { rows.add(new RawTextLayout.Line(start,cursor)); start=++cursor; preferred=-1; continue; }
            int next=text.offsetByCodePoints(cursor,1);
            if(cursor>start && distance(start,next)>Math.max(1,width)) {
                int end=preferred>start?preferred:cursor;
                rows.add(new RawTextLayout.Line(start,end)); start=cursor=end; preferred=-1; continue;
            }
            cursor=next;
            if(MixedTextLayout.preferredBoundary(text,cursor)) preferred=cursor;
        }
        rows.add(new RawTextLayout.Line(start,text.length())); lines=List.copyOf(rows);
    }
    public List<RawTextLayout.Line> lines() { return lines; }
    public double distance(int start,int end) { return advances[TextEditBuffer.boundary(text,end)]-advances[TextEditBuffer.boundary(text,start)]; }
    public int row(int caret) {
        int safe=TextEditBuffer.boundary(text,caret);
        // Find the last row starting at/before the caret. A newline remains on the preceding row until passed.
        int low=0,high=lines.size()-1;
        while(low<high) {
            int middle=(low+high+1)>>>1;
            if(lines.get(middle).start()<=safe) low=middle; else high=middle-1;
        }
        return low;
    }
    public int hit(int row,double x) {
        var line=lines.get(Math.clamp(row,0,lines.size()-1));
        for(int index=line.start();index<line.end();) {
            int next=text.offsetByCodePoints(index,1);
            double middle=(distance(line.start(),index)+distance(line.start(),next))/2;
            if(x<middle) return index; index=next;
        }
        return line.end();
    }
}

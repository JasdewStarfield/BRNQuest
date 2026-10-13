package yourscraft.jasdewstarfield.brnquest.client.ui;

import java.util.ArrayList;
import java.util.List;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Logical text geometry is measured once; placement and notification avoidance use its scaled screen bounds. */
final class QuestHudLayout {
    static final int TEXT_LEFT = 30, RIGHT_PADDING = 8, INLINE_GAP = 8;
    static final int MAX_OBJECTIVES = 3;
    record Measure(int titleWidth, int progressWidth) {
        boolean inline(int textWidth) { return titleWidth + INLINE_GAP + progressWidth <= textWidth; }
    }
    record Row(int top, int height, boolean inline) {}
    record Plan(int width, int height, int headerHeight, List<Row> rows, int hidden, UiRect screenBounds) {}
    private QuestHudLayout() {}

    static int width(int screenWidth, double scale, int configuredMaximum, int titleWidth, List<Measure> rows) {
        int available = Math.max(1, (int)Math.floor(Math.max(1,screenWidth-16)/scale));
        int content = titleWidth + 30;
        for (Measure row : rows) content = Math.max(content, TEXT_LEFT + RIGHT_PADDING + row.titleWidth()+INLINE_GAP+row.progressWidth());
        return Math.min(available, Math.min(configuredMaximum, Math.max(120, content)));
    }

    /** Short rows share a baseline; long requirements reserve their wrapped lines plus a separate progress line. */
    static Plan plan(int screenWidth, int screenHeight, double scale, int width, int headerLines,
                     int lineHeight, List<Measure> measures, List<Integer> titleLines, int totalObjectives) {
        int header = 6 + Math.max(10, Math.min(2,headerLines)*lineHeight) + 6;
        int availableHeight = Math.max(0,(int)Math.floor((screenHeight-18)/scale));
        int footerHeight = lineHeight+4;
        int top = header;
        var rows = new ArrayList<Row>();
        for (int i=0;i<Math.min(MAX_OBJECTIVES,measures.size());i++) {
            boolean inline = measures.get(i).inline(width-TEXT_LEFT-RIGHT_PADDING);
            int height = Math.max(16, inline ? lineHeight : (Math.min(2,titleLines.get(i))+1)*lineHeight)+4;
            // Account for the omitted-count footer before deciding whether another objective fits.
            int footer = totalObjectives > i+1 ? footerHeight : 0;
            if(top+height+footer+6>availableHeight) break;
            rows.add(new Row(top,height,inline)); top+=height;
        }
        int hidden = Math.max(0,totalObjectives-rows.size());
        int height = top + (hidden>0 ? footerHeight : 0) + 6;
        int pixelsWidth = (int)Math.ceil(width*scale), pixelsHeight = (int)Math.ceil(height*scale);
        int left = Math.max(0,screenWidth-8-pixelsWidth);
        UiRect bounds = new UiRect(left,10,left+pixelsWidth,10+pixelsHeight);
        return new Plan(width,height,header,List.copyOf(rows),hidden,
                bounds.bottom()<=screenHeight-8 ? bounds : null);
    }
}

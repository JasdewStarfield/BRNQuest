package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import yourscraft.jasdewstarfield.brnquest.data.text.MinecraftStyledText;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded source cache: repeated HUD and canvas frames reuse parsed spans, never random obfuscated glyphs. */
public final class NativeText {
    private static final Map<String, Component> CACHE = new LinkedHashMap<>(128, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Component> entry) { return size() > 512; }
    };
    private NativeText() {}

    public static MutableComponent component(String raw) {
        String source = raw == null ? "" : raw;
        return CACHE.computeIfAbsent(source, value -> {
            MutableComponent result = Component.empty();
            for (var run : MinecraftStyledText.parse(value).runs()) {
                var parsed = run.style();
                Style style = Style.EMPTY.withBold(parsed.bold()).withItalic(parsed.italic())
                        .withUnderlined(parsed.underlined()).withStrikethrough(parsed.strikethrough())
                        .withObfuscated(parsed.obfuscated());
                if (parsed.color() != null) style = style.withColor(parsed.color());
                result.append(Component.literal(run.text()).withStyle(style));
            }
            return result;
        }).copy();
    }

    public static String visible(String raw) { return component(raw).getString(); }

    /** Literal sequences keep unknown section codes visible and retain styles across clipping. */
    public static FormattedCharSequence sequence(Component text) {
        List<FormattedCharSequence> runs = new ArrayList<>();
        text.visit((style, value) -> {
            runs.add(FormattedCharSequence.forward(value, style));
            return Optional.empty();
        }, Style.EMPTY);
        return FormattedCharSequence.composite(runs);
    }

    private record Glyph(int offset, Style style, int codepoint) {}
    public static FormattedCharSequence slice(Component text, int start, int end) {
        List<FormattedCharSequence> runs = new ArrayList<>();
        int[] offset = {0};
        sequence(text).accept((ignored, style, codepoint) -> {
            int next = offset[0]+Character.charCount(codepoint);
            if (offset[0]>=start && next<=end) runs.add(FormattedCharSequence.codepoint(codepoint,style));
            offset[0]=next;
            return true;
        });
        return FormattedCharSequence.composite(runs);
    }

    /** Style-aware punctuation wrapping; every glyph is measured with the style used for drawing. */
    public static List<FormattedCharSequence> split(Font font, Component text, int maximumWidth) {
        String visible = text.getString();
        List<Glyph> glyphs = new ArrayList<>();
        int[] offset = {0};
        sequence(text).accept((ignored, style, codepoint) -> {
            glyphs.add(new Glyph(offset[0],style,codepoint));
            offset[0]+=Character.charCount(codepoint); return true;
        });
        float[] widths = new float[visible.length()+1];
        for (var glyph:glyphs) {
            int end=glyph.offset()+Character.charCount(glyph.codepoint());
            widths[end]=widths[glyph.offset()]+font.getSplitter().stringWidth(FormattedCharSequence.codepoint(glyph.codepoint(),glyph.style()));
        }
        List<FormattedCharSequence> result=new ArrayList<>();
        int start=0, cursor=0, preferred=-1;
        while(cursor<visible.length()) {
            if(visible.charAt(cursor)=='\n') {
                result.add(slice(text,start,cursor)); start=++cursor; preferred=-1; continue;
            }
            int next=visible.offsetByCodePoints(cursor,1);
            if(cursor>start && widths[next]-widths[start]>Math.max(1,maximumWidth)) {
                int end=preferred>start?preferred:MixedTextLayout.legalBoundary(visible,cursor)?cursor:-1;
                if(end>=0) { result.add(slice(text,start,end)); start=cursor=end; preferred=-1; continue; }
            }
            cursor=next;
            if(MixedTextLayout.preferredBoundary(visible,cursor)) preferred=cursor;
        }
        result.add(slice(text,start,visible.length()));
        return List.copyOf(result);
    }
}

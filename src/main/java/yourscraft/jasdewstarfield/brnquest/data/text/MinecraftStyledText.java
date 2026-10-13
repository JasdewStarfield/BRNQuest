package yourscraft.jasdewstarfield.brnquest.data.text;

import java.util.ArrayList;
import java.util.List;

/** Renderer-neutral native formatting shared by labels, searching and authoring commands. */
public record MinecraftStyledText(String visibleText, List<Run> runs) {
    public record Run(String text, RichDocument.InlineStyle style) {}

    public static MinecraftStyledText parse(String raw) {
        List<Run> runs = new ArrayList<>();
        for (RichDocument.Block block : MinecraftLegacyTextParser.parse(raw).blocks()) {
            if (block instanceof RichDocument.LiteralBlock literal)
                runs.add(new Run(literal.text(), RichDocument.InlineStyle.EMPTY));
            else if (block instanceof RichDocument.FlowBlock flow) {
                for (RichDocument.Inline inline : flow.inlines()) {
                    if (inline instanceof RichDocument.Text text)
                        runs.add(new Run(text.value(), RichDocument.InlineStyle.EMPTY));
                    else if (inline instanceof RichDocument.StyleSpan span)
                        for (RichDocument.Inline child : span.children())
                            if (child instanceof RichDocument.Text text) runs.add(new Run(text.value(), span.style()));
                }
            }
        }
        return new MinecraftStyledText(runs.stream().map(Run::text).collect(java.util.stream.Collectors.joining()),
                List.copyOf(runs));
    }
}

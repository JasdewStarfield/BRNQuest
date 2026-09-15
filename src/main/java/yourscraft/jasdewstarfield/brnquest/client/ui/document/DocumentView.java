package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import yourscraft.jasdewstarfield.brnquest.data.text.MarkdownParserAdapter;
import yourscraft.jasdewstarfield.brnquest.data.text.ResolvedDocument;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

import java.net.URI;

/** Cached parser/layout bridge. Callers own viewport scissoring and scroll translation. */
public final class DocumentView {
    private final MarkdownParserAdapter parser;
    private final DocumentLayoutEngine engine;
    private CacheKey cachedKey;
    private Prepared cached;

    public DocumentView() {
        this(new MarkdownParserAdapter(), new DocumentLayoutEngine());
    }

    DocumentView(MarkdownParserAdapter parser, DocumentLayoutEngine engine) {
        this.parser = parser;
        this.engine = engine;
    }

    /** Reuses the immutable result until content, locale, geometry, font, or resources change. */
    public Prepared prepare(ResolvedDocument source, String locale, int width, long fontGeneration,
                            long guiScaleGeneration, long resourceGeneration, DocumentLayoutEngine.Metrics metrics) {
        CacheKey key = new CacheKey(source.text(), source.format().serializedName(), MarkdownParserAdapter.PARSER_VERSION,
                BookLocalization.normalizeLocale(locale), Math.max(1, width), fontGeneration, guiScaleGeneration,
                resourceGeneration);
        if (key.equals(cachedKey)) return cached;
        RichDocument document = parser.parse(source);
        cachedKey = key;
        cached = new Prepared(document, engine.layout(document, key.width, metrics));
        return cached;
    }

    public static DocumentLayoutEngine.Metrics minecraftMetrics(Font font) {
        return new DocumentLayoutEngine.Metrics() {
            @Override public int width(String text, DocumentTextStyle style) {
                return Math.max(0, Math.round(font.width(styled(text, style)) * style.scale()));
            }

            @Override public int lineHeight(DocumentTextStyle style) {
                return Math.max(1, Math.round(font.lineHeight * style.scale()));
            }
        };
    }

    /** Draws exactly the cached runs; it deliberately leaves the caller's scissor state untouched. */
    public static void render(GuiGraphics graphics, Font font, DocumentLayout layout, int x, int y, int color) {
        for (DocumentLayout.Line line : layout.lines()) {
            for (DocumentLayout.Run run : line.runs()) {
                float scale = run.style().scale();
                graphics.pose().pushPose();
                graphics.pose().translate(x + run.bounds().left(), y + run.bounds().top(), 0);
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.drawString(font, styled(run.text(), run.style()), 0, 0, color, false);
                graphics.pose().popPose();
            }
        }
    }

    /** A link is active only when both its run and the pointer lie inside the actual viewport. */
    public static URI linkAt(DocumentLayout layout, int documentX, int documentY,
                             DocumentLayout.Bounds viewport) {
        if (!viewport.contains(documentX, documentY)) return null;
        for (DocumentLayout.LinkHit link : layout.links())
            if (link.bounds().intersects(viewport) && link.bounds().contains(documentX, documentY))
                return link.destination();
        return null;
    }

    private static Component styled(String text, DocumentTextStyle documentStyle) {
        Style style = Style.EMPTY.withBold(documentStyle.bold()).withItalic(documentStyle.italic());
        if (documentStyle.code()) style = style.withFont(ResourceLocation.withDefaultNamespace("uniform"));
        if (documentStyle.link() != null) style = style.withUnderlined(true).withColor(0x68BDE8);
        return Component.literal(text).withStyle(style);
    }

    public record Prepared(RichDocument document, DocumentLayout layout) {}

    private record CacheKey(String text, String format, int parserVersion, String locale, int width,
                            long fontGeneration, long guiScaleGeneration, long resourceGeneration) {}
}

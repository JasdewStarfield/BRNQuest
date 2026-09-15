package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.LoadedTextures;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
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

            @Override public DocumentLayoutEngine.ContentSize contentSize(RichDocument.Content content) {
                ResourceLocation id = ResourceLocation.tryParse(content.id());
                if (id == null) return new DocumentLayoutEngine.ContentSize(24, 24, false);
                if (content.kind() == RichDocument.ContentKind.ITEM) {
                    boolean present = BuiltInRegistries.ITEM.containsKey(id);
                    return new DocumentLayoutEngine.ContentSize(present ? 16 : 24, present ? 16 : 24, present);
                }
                LoadedTextures.Size size = LoadedTextures.size(id);
                return new DocumentLayoutEngine.ContentSize(size.present() ? size.width() : 24,
                        size.present() ? size.height() : 24, size.present());
            }
        };
    }

    /** Draws exactly the cached runs; it deliberately leaves the caller's scissor state untouched. */
    public static void render(GuiGraphics graphics, Font font, DocumentLayout layout, int x, int y, int color) {
        for (DocumentLayout.Line line : layout.lines()) {
            for (DocumentLayout.Run run : line.runs()) {
                float scale = run.style().scale();
                if (run.style().code()) graphics.fill(x + run.bounds().left() - 1, y + run.bounds().top() - 1,
                        x + run.bounds().right() + 1, y + run.bounds().bottom(), GraystonePalette.INLINE_CODE);
                graphics.pose().pushPose();
                graphics.pose().translate(x + run.bounds().left(), y + run.bounds().top(), 0);
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.drawString(font, styled(run.text(), run.style()), 0, 0, color, false);
                graphics.pose().popPose();
            }
        }
        for (DocumentLayout.ContentHit hit : layout.contents()) renderContent(graphics, font, hit, x, y);
    }

    private static void renderContent(GuiGraphics graphics, Font font, DocumentLayout.ContentHit hit, int x, int y) {
        int left = x + hit.bounds().left();
        int top = y + hit.bounds().top();
        int width = hit.bounds().width();
        int height = hit.bounds().height();
        if (hit.content().kind() == RichDocument.ContentKind.TEXTURE) {
            LoadedTextures.draw(graphics, hit.content().id(), left, top, width, height, 1.0);
            if (!hit.present()) drawMissingContent(graphics, font, hit.content(), left, top, width, height);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(hit.content().id());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            ItemStack stack = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            if (!stack.isEmpty()) {
                // Extremely narrow viewports can shrink an inline item below vanilla's fixed 16 px render size.
                graphics.pose().pushPose();
                graphics.pose().translate(left, top, 0);
                graphics.pose().scale(width / 16.0F, height / 16.0F, 1.0F);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            }
            else drawMissingContent(graphics, font, hit.content(), left, top, width, height);
        } else drawMissingContent(graphics, font, hit.content(), left, top, width, height);
    }

    /** Missing client resources stay visible and keep their alt text available through hover. */
    private static void drawMissingContent(GuiGraphics graphics, Font font, RichDocument.Content content,
                                           int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF572958);
        graphics.renderOutline(x, y, width, height, 0xFFE3A8E3);
        graphics.drawCenteredString(font, Component.literal("?"), x + width / 2,
                y + Math.max(1, (height - font.lineHeight) / 2), 0xFFFFFFFF);
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

    public static DocumentLayout.ContentHit contentAt(DocumentLayout layout, int documentX, int documentY,
                                                       DocumentLayout.Bounds viewport) {
        if (!viewport.contains(documentX, documentY)) return null;
        for (DocumentLayout.ContentHit content : layout.contents())
            if (content.bounds().intersects(viewport) && content.bounds().contains(documentX, documentY))
                return content;
        return null;
    }

    /** Item content has native hover information but deliberately exposes no click action. */
    public static ItemStack itemAt(DocumentLayout layout, int documentX, int documentY,
                                   DocumentLayout.Bounds viewport) {
        DocumentLayout.ContentHit hit = contentAt(layout, documentX, documentY, viewport);
        if (hit == null || hit.content().kind() != RichDocument.ContentKind.ITEM) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(hit.content().id());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
    }

    private static Component styled(String text, DocumentTextStyle documentStyle) {
        Style style = Style.EMPTY.withBold(documentStyle.bold()).withItalic(documentStyle.italic())
                .withUnderlined(documentStyle.underlined()).withStrikethrough(documentStyle.strikethrough())
                .withObfuscated(documentStyle.obfuscated());
        if (documentStyle.color() != null) style = style.withColor(documentStyle.color());
        if (documentStyle.code()) style = style.withFont(ResourceLocation.withDefaultNamespace("uniform"));
        if (documentStyle.link() != null) {
            style = style.withUnderlined(true);
            if (documentStyle.color() == null) style = style.withColor(0x68BDE8);
        }
        return Component.literal(text).withStyle(style);
    }

    public record Prepared(RichDocument document, DocumentLayout layout) {}

    private record CacheKey(String text, String format, int parserVersion, String locale, int width,
                            long fontGeneration, long guiScaleGeneration, long resourceGeneration) {}
}

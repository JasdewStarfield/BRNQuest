package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.MixedTextLayout;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;
import yourscraft.jasdewstarfield.brnquest.data.text.MarkdownParserAdapter;
import yourscraft.jasdewstarfield.brnquest.data.text.ResolvedDocument;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers geometry risks that builds and client screenshots cannot isolate reliably. */
class DocumentLayoutEngineTest {
    private static final DocumentLayoutEngine.Metrics METRICS = new DocumentLayoutEngine.Metrics() {
        @Override public int width(String text, DocumentTextStyle style) {
            return Math.round(text.codePointCount(0, text.length()) * style.scale());
        }

        @Override public int lineHeight(DocumentTextStyle style) {
            return Math.round(10 * style.scale());
        }
    };

    private final MarkdownParserAdapter parser = new MarkdownParserAdapter();
    private final DocumentLayoutEngine engine = new DocumentLayoutEngine();

    @Test void mixedStylesShareOneMeasuredLayoutAndLinkGeometry() {
        DocumentLayout layout = layout("中文 BRNQuest **粗体** [链接](https://example.test/path)", 10);
        assertTrue(layout.lines().size() > 1, layout.toString());
        assertTrue(layout.lines().stream().flatMap(line -> line.runs().stream()).anyMatch(run -> run.style().bold()));
        assertFalseLinksOutsideContent(layout);
        assertEquals(layout.lines().getLast().bounds().bottom(), layout.contentHeight());
    }

    @Test void plainTextKeepsTheExistingMixedLanguageWrappingContract() {
        String source = "任务 BRNQuest 甲乙，丙（丁） brnquest:very_long_identifier";
        RichDocument document = parser.parse(new ResolvedDocument(source, DocumentFormat.PLAIN, "zh_cn"));
        List<String> actual = engine.layout(document, 10, METRICS).lines().stream().map(line -> line.runs().stream()
                .map(DocumentLayout.Run::text).reduce("", String::concat)).toList();
        assertEquals(MixedTextLayout.wrap(source, 10, text -> text.codePointCount(0, text.length())), actual);
    }

    @Test void legacyCodesDoNotOccupyLayoutWidthAndColorResetsEarlierDecorations() {
        RichDocument document = parser.parse(new ResolvedDocument(
                "A§lBC§cD§nE§rF", DocumentFormat.PLAIN, "en_us"));
        DocumentLayout layout = engine.layout(document, 20, METRICS);
        List<DocumentLayout.Run> runs = layout.lines().getFirst().runs();

        assertEquals("ABCDEF", runs.stream().map(DocumentLayout.Run::text).reduce("", String::concat));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("BC") && run.style().bold()));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("D")
                && Integer.valueOf(0xFF5555).equals(run.style().color()) && !run.style().bold()));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("E") && run.style().underlined()));
        assertTrue(runs.stream().anyMatch(run -> run.text().equals("F") && run.style().color() == null));
    }

    @Test void punctuationEmojiAndLongIdentifiersAlwaysAdvanceAtSafeBoundaries() {
        DocumentLayout layout = layout("甲乙，丙（丁） 👨‍👩‍👧‍👦 brnquest:very_long_identifier", 8);
        List<String> lines = layout.lines().stream().map(line -> line.runs().stream()
                .map(DocumentLayout.Run::text).reduce("", String::concat)).toList();
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("，") || line.endsWith("（")));
        assertTrue(lines.stream().allMatch(line -> !line.isEmpty()));
        String visible = String.join("", lines);
        assertTrue(visible.contains("👨‍👩‍👧‍👦"));
        assertTrue(hasNoUnpairedSurrogates(visible));
    }

    @Test void explicitLinesListsAndNarrowWidthsHaveStableHeight() {
        DocumentLayout layout = layout("# Head\n\nfirst  \nsecond\n\n- long-list-item\n- 二", 5);
        assertTrue(layout.lines().size() >= 6);
        assertTrue(layout.lines().stream().allMatch(line -> line.bounds().height() > 0));
        assertEquals(layout.lines().getLast().bounds().bottom(), layout.contentHeight());
    }

    @Test void layoutUsesTheSuppliedFontProportionsInsteadOfCharacterCounts() {
        RichDocument document = parser.parse(new ResolvedDocument("WWii WWii", DocumentFormat.MARKDOWN_V1, "en_us"));
        DocumentLayoutEngine.Metrics proportional = new DocumentLayoutEngine.Metrics() {
            @Override public int width(String text, DocumentTextStyle style) {
                return text.codePoints().map(codePoint -> codePoint == 'W' ? 4 : 1).sum();
            }

            @Override public int lineHeight(DocumentTextStyle style) { return 9; }
        };
        DocumentLayout layout = engine.layout(document, 10, proportional);
        assertTrue(layout.lines().size() > 1);
        assertTrue(layout.lines().stream().flatMap(line -> line.runs().stream())
                .allMatch(run -> run.bounds().right() <= layout.width()));
    }

    @Test void linkHitTestingRejectsAllFourViewportDirections() {
        DocumentLayout layout = layout("[target](https://example.test)", 20);
        DocumentLayout.Bounds link = layout.links().getFirst().bounds();
        DocumentLayout.Bounds viewport = new DocumentLayout.Bounds(link.left(), link.top(), link.right(), link.bottom());
        assertEquals(URI.create("https://example.test"), DocumentView.linkAt(layout, link.left(), link.top(), viewport));
        assertNull(DocumentView.linkAt(layout, link.left() - 1, link.top(), viewport));
        assertNull(DocumentView.linkAt(layout, link.right(), link.top(), viewport));
        assertNull(DocumentView.linkAt(layout, link.left(), link.top() - 1, viewport));
        assertNull(DocumentView.linkAt(layout, link.left(), link.bottom(), viewport));
    }

    @Test void cacheKeyIncludesLocaleWidthFontAndResourceGenerations() {
        DocumentView view = new DocumentView(parser, engine);
        ResolvedDocument source = new ResolvedDocument("text", DocumentFormat.MARKDOWN_V1, "en_us");
        DocumentView.Prepared initial = view.prepare(source, "en_us", 20, 1, 1, 1, METRICS);
        assertSame(initial, view.prepare(source, "en-US", 20, 1, 1, 1, METRICS));
        assertNotSame(initial, view.prepare(source, "zh_cn", 20, 1, 1, 1, METRICS));
        DocumentView.Prepared locale = view.prepare(source, "zh_cn", 20, 1, 1, 1, METRICS);
        assertNotSame(locale, view.prepare(source, "zh_cn", 19, 1, 1, 1, METRICS));
        DocumentView.Prepared width = view.prepare(source, "zh_cn", 19, 1, 1, 1, METRICS);
        assertNotSame(width, view.prepare(source, "zh_cn", 19, 2, 1, 1, METRICS));
        DocumentView.Prepared font = view.prepare(source, "zh_cn", 19, 2, 1, 1, METRICS);
        assertNotSame(font, view.prepare(source, "zh_cn", 19, 2, 2, 1, METRICS));
        DocumentView.Prepared scale = view.prepare(source, "zh_cn", 19, 2, 2, 1, METRICS);
        assertNotSame(scale, view.prepare(source, "zh_cn", 19, 2, 2, 2, METRICS));
    }

    @Test void contentUsesMeasuredAspectRatioBoundedHeightAndViewportHits() {
        RichDocument document = parser.parse(new ResolvedDocument(
                "![tall](texture:brnquest:textures/tall.png)", DocumentFormat.MARKDOWN_V1, "en_us"));
        DocumentLayoutEngine.Metrics resources = new DocumentLayoutEngine.Metrics() {
            @Override public int width(String text, DocumentTextStyle style) { return text.length(); }
            @Override public int lineHeight(DocumentTextStyle style) { return 10; }
            @Override public DocumentLayoutEngine.ContentSize contentSize(RichDocument.Content content) {
                return new DocumentLayoutEngine.ContentSize(100, 1000, true);
            }
        };
        DocumentLayout layout = engine.layout(document, 400, resources);
        DocumentLayout.ContentHit hit = layout.contents().getFirst();
        assertEquals(16, hit.bounds().width());
        assertEquals(DocumentLayoutEngine.MAX_CONTENT_HEIGHT, hit.bounds().height());
        assertEquals(hit.bounds().bottom(), layout.contentHeight());
        assertEquals(hit, DocumentView.contentAt(layout, hit.bounds().left(), hit.bounds().top(), hit.bounds()));
        assertNull(DocumentView.contentAt(layout, hit.bounds().right(), hit.bounds().top(), hit.bounds()));
    }

    @Test void inlineContentWrapsAtomicallyAndSharesTheTextBaseline() {
        RichDocument document = parser.parse(new ResolvedDocument(
                "before ![stone](item:minecraft:stone) after ![wide](texture:brnquest:textures/wide.png) end",
                DocumentFormat.MARKDOWN_V1, "en_us"));
        DocumentLayoutEngine.Metrics resources = new DocumentLayoutEngine.Metrics() {
            @Override public int width(String text, DocumentTextStyle style) { return text.length(); }
            @Override public int lineHeight(DocumentTextStyle style) { return 10; }
            @Override public DocumentLayoutEngine.ContentSize contentSize(RichDocument.Content content) {
                return content.kind() == RichDocument.ContentKind.ITEM
                        ? new DocumentLayoutEngine.ContentSize(16, 16, true)
                        : new DocumentLayoutEngine.ContentSize(64, 16, true);
            }
        };

        DocumentLayout layout = engine.layout(document, 30, resources);
        assertEquals(2, layout.contents().size());
        DocumentLayout.ContentHit item = layout.contents().getFirst();
        DocumentLayout.ContentHit texture = layout.contents().getLast();
        assertEquals(16, item.bounds().width());
        assertEquals(16, item.bounds().height());
        assertTrue(texture.bounds().width() <= 30);
        assertTrue(texture.bounds().height() <= DocumentLayoutEngine.INLINE_CONTENT_HEIGHT);
        assertTrue(layout.contents().stream().allMatch(hit -> hit.bounds().right() <= layout.width()));
        assertTrue(layout.lines().stream().anyMatch(line -> line.baseline() == item.bounds().bottom()
                && line.runs().stream().allMatch(run -> run.bounds().bottom() == line.baseline())));
    }

    private DocumentLayout layout(String source, int width) {
        RichDocument document = parser.parse(new ResolvedDocument(source, DocumentFormat.MARKDOWN_V1, "zh_cn"));
        return engine.layout(document, width, METRICS);
    }

    private static void assertFalseLinksOutsideContent(DocumentLayout layout) {
        assertTrue(layout.links().stream().allMatch(link -> link.bounds().left() >= 0
                && link.bounds().right() <= layout.width() && link.bounds().bottom() <= layout.contentHeight()));
    }

    private static boolean hasNoUnpairedSurrogates(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.isSurrogate(text.charAt(offset)) && Character.charCount(codePoint) == 1) return false;
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownPreviewStateTest {
    @Test void coalescesRapidChangesUntilTheLatestDeadline() {
        var state = new MarkdownPreviewState();
        state.showNow("old", DocumentFormat.PLAIN, "zh_cn");
        state.schedule("first", DocumentFormat.MARKDOWN_V1, "zh_cn", 1000);
        state.schedule("latest", DocumentFormat.MARKDOWN_V1, "zh_cn", 1100);

        assertFalse(state.advance(1249));
        assertEquals("old", state.visible().text());
        assertTrue(state.advance(1250));
        assertEquals("latest", state.visible().text());
        assertEquals(DocumentFormat.MARKDOWN_V1, state.visible().format());
    }
}

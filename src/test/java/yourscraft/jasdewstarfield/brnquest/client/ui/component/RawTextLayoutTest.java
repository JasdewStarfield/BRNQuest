package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RawTextLayoutTest {
    @Test void sourceInputPreservesFormattingButFiltersControlCharacters() {
        assertEquals("§6§l你好😀",RawTextLayout.filter("§6§l你好😀\n\u0000",false));
        assertEquals("§6Hello\nWorld",RawTextLayout.filter("§6Hello\nWorld\r",true));
    }
    @Test void literalWrappingKeepsEverySourceCharacterAndExplicitBlankLines() {
        String source="§6AB😀CD\n\nEF\n";
        var lines=RawTextLayout.lines(source,3,s->s.codePointCount(0,s.length()));
        assertEquals(new RawTextLayout.Line(source.length(),source.length()),lines.getLast());
        String joined=lines.stream().map(l->source.substring(l.start(),l.end())).reduce("",String::concat);
        assertEquals(source.replace("\n",""),joined);
        assertTrue(lines.stream().anyMatch(l->l.start()==l.end()));
        for(var line:lines) assertTrue(line.start()==source.length() || !Character.isLowSurrogate(source.charAt(line.start())));
    }
}

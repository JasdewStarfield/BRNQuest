package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnbtReaderTest {
    @Test void parsesNumbersEscapesListsAndCompounds() throws Exception {
        var tag = TagParser.parseTag("{long: 3L, decimal: 0.5d, text: \"a\\\"b\", list: [1, 2], nested: {ok: true}}");
        assertEquals(3L, tag.getLong("long"));
        assertEquals(0.5, tag.getDouble("decimal"));
        assertEquals("a\"b", tag.getString("text"));
        assertEquals(2, tag.getList("list", 3).size());
        assertTrue(tag.getCompound("nested").getBoolean("ok"));
    }

    @Test void rejectsMalformedSnbt() { assertThrows(Exception.class, () -> TagParser.parseTag("{broken: [}")); }

    @Test void normalizesFtbLineDelimitedFields() throws Exception {
        String normalized = SnbtReader.addStructuralCommas("{\n  a: 1\n  nested: {\n    ok: true\n  }\n  list: [\n    { id: \"A\" }\n    { id: \"B\" }\n  ]\n}");
        assertEquals(2, TagParser.parseTag(normalized).getList("list", 10).size());
    }
}

package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SnbtReaderTest {
    @TempDir Path directory;

    @Test void readsMultilineTypedArraysWithoutChangingTheirValues() throws Exception {
        // Exercise the public reader's fallback with storage IDs, nested positions and blueprint bytes.
        Path source = directory.resolve("typed-arrays.snbt");
        Files.writeString(source, """
                {
                    ints: [I;
                        -1048080236
                        -1127789896
                        -1563483338
                        -862978119
                    ]
                    bytes: [B;
                        -128b
                        0b
                        127b
                    ]
                    longs: [L;
                        -9223372036854775808L
                        9223372036854775807L
                    ]
                    positions: [[I;
                        -7807
                        192
                        2098
                    ]]
                    empty: [I;
                    ]
                    after: "still present"
                }
                """);
        var tag = new SnbtReader().read(source);
        assertArrayEquals(new int[]{-1048080236, -1127789896, -1563483338, -862978119}, tag.getIntArray("ints"));
        assertArrayEquals(new byte[]{-128, 0, 127}, tag.getByteArray("bytes"));
        assertArrayEquals(new long[]{Long.MIN_VALUE, Long.MAX_VALUE}, tag.getLongArray("longs"));
        assertArrayEquals(new int[]{-7807, 192, 2098}, tag.getList("positions", 11).getIntArray(0));
        assertArrayEquals(new int[0], tag.getIntArray("empty"));
        assertEquals("still present", tag.getString("after"));
    }

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

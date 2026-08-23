package yourscraft.jasdewstarfield.brnquest.editor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RawConfigTextTest {
    @Test
    void formatIsDeterministicAndRoundTripsStringValues() {
        Map<String, String> config = Map.of("zeta", "true", "alpha", "{id:\"minecraft:stone\"}");

        String formatted = RawConfigText.format(config);

        assertEquals(config, RawConfigText.parse(formatted));
        org.junit.jupiter.api.Assertions.assertTrue(formatted.indexOf("alpha") < formatted.indexOf("zeta"));
    }

    @Test
    void primitiveJsonInputNormalizesToTheSchemaStringMap() {
        assertEquals(Map.of("count", "3", "enabled", "true", "title", "Example"),
                RawConfigText.parse("{\"count\":3,\"enabled\":true,\"title\":\"Example\"}"));
    }

    @Test
    void nestedOrMalformedJsonCannotEnterThePropertyForm() {
        assertThrows(IllegalArgumentException.class, () -> RawConfigText.parse("{\"nested\":{\"value\":1}}"));
        assertThrows(IllegalArgumentException.class, () -> RawConfigText.parse("[1,2,3]"));
        assertThrows(IllegalArgumentException.class, () -> RawConfigText.parse("{broken"));
    }
}

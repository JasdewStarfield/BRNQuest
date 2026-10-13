package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Invalid modes and counter limits must fail rather than silently changing the requested semantics. */
class StatisticsConfigTest {
    @Test void rawIntegerBoundsAndModeAreStrict() {
        assertEquals(1, StatisticsConfig.parse(Map.of()).count());
        assertEquals(Integer.MAX_VALUE, StatisticsConfig.parse(Map.of("count", "2147483647")).count());
        for (var values : java.util.List.of(Map.of("count", "0"), Map.of("count", "2147483648"),
                Map.of("count", "1.5"), Map.of("count", "-1"), Map.of("stat", "#minecraft:logs"),
                Map.of("stat_type", "example:category"), Map.of("mode", "since_available")))
            assertThrows(IllegalArgumentException.class, () -> StatisticsConfig.parse(values));
    }
    @Test void normalizationRetainsOpaqueConfigurationAndDependentPicker() {
        var normalized = StatisticsConfig.normalize(Map.of("stat", " minecraft:jump ", "count", " 20 ", "extension", " kept "));
        assertEquals("minecraft:jump", normalized.get("stat"));
        assertEquals(" kept ", normalized.get("extension"));
        assertTrue(StatisticsConfig.fields().stream().filter(field -> field.key().equals("stat")).findFirst().orElseThrow().serverSource().isPresent());
        assertEquals(9, StatisticsConfig.TYPES.size());
    }
}

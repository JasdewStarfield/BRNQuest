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
                Map.of("stat_type", "example:category"), Map.of("mode", "unknown")))
            assertThrows(IllegalArgumentException.class, () -> StatisticsConfig.parse(values));
    }
    @Test void normalizationRetainsOpaqueConfigurationAndDependentPicker() {
        var normalized = StatisticsConfig.normalize(Map.of("stat", " minecraft:jump ", "count", " 20 ", "extension", " kept "));
        assertEquals("minecraft:jump", normalized.get("stat"));
        assertEquals(" kept ", normalized.get("extension"));
        assertTrue(StatisticsConfig.fields().stream().filter(field -> field.key().equals("stat")).findFirst().orElseThrow().serverSource().isPresent());
        assertEquals(9, StatisticsConfig.TYPES.size());
    }
    @Test void everyCategoryHasStandaloneBilingualNamesWithoutFormattingArguments() throws Exception {
        var field = StatisticsConfig.fields().stream().filter(value -> value.key().equals("stat_type")).findFirst().orElseThrow();
        for (String locale : java.util.List.of("en_us", "zh_cn")) {
            try (var stream = getClass().getResourceAsStream("/assets/brnquest/lang/" + locale + ".json")) {
                assertNotNull(stream);
                var language = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String type : StatisticsConfig.TYPES) {
                    String key = field.valueLabelKeys().get(type);
                    assertTrue(language.has(key), type + " must have an actual category translation");
                    assertFalse(language.get(key).getAsString().contains("%"), type + " must not require entity/count arguments");
                    var name = StatisticsTargets.categoryName(new StatisticsConfig(net.minecraft.resources.ResourceLocation.parse(type),
                            net.minecraft.resources.ResourceLocation.parse("minecraft:jump"), 1));
                    var contents = (net.minecraft.network.chat.contents.TranslatableContents) name.getContents();
                    assertEquals(key, contents.getKey(), "requirements must use the same standalone label as the picker");
                    assertEquals(0, contents.getArgs().length);
                }
            }
        }
    }
}

package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.nbt.TagParser;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics.StatisticsConfig;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** SNBT fixtures specify storage and gameplay expectations, with no source-mod implementation dependency. */
class FtbStatisticFieldsTest {
    @TempDir Path temporary;
    @Test void customHistoricalRawValuesAndOpaqueFieldsArePreserved() throws Exception {
        var raw = TagParser.parseTag("{stat:'minecraft:jump',value:100L,opaque:'keep'}");
        var config = new HashMap<String, String>();
        config.put("opaque", raw.get("opaque").toString());
        assertEquals("", FtbStatisticFields.apply(StatisticsConfig.ID, raw, config));
        var parsed = StatisticsConfig.parse(config);
        assertEquals("minecraft:custom", parsed.statType().toString());
        assertEquals("minecraft:jump", parsed.stat().toString());
        assertEquals(100, parsed.count());
        assertEquals("total", parsed.mode());
        assertTrue(parsed.rawFormat());
        assertTrue(parsed.legacyNamespace());
        assertEquals(raw.get("stat").toString(), config.get("ftb.stat"));
        assertEquals("100L", config.get("ftb.value"));
        assertEquals(raw.get("opaque").toString(), config.get("opaque"));
    }
    @Test void malformedThresholdsStayDiagnosticAndCannotExecute() throws Exception {
        for (String snbt : List.of("{stat:'minecraft:jump',value:0}", "{stat:'minecraft:jump',value:2147483648L}",
                "{stat:'minecraft:jump',value:1.5d}", "{stat:'bad id',value:1}", "{stat:'minecraft:jump'}")) {
            var config = new HashMap<String, String>();
            assertFalse(FtbStatisticFields.apply(StatisticsConfig.ID, TagParser.parseTag(snbt), config).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> StatisticsConfig.parse(config));
        }
    }
    @Test void authorCanRepairInvalidFieldsWhileRetainingOriginalSnbtEvidence() throws Exception {
        var config = new HashMap<String, String>();
        FtbStatisticFields.apply(StatisticsConfig.ID, TagParser.parseTag("{stat:'minecraft:jump',value:0}"), config);
        config.put("count", "2");
        assertEquals(2, StatisticsConfig.parse(config).count());
        assertEquals("0", config.get("ftb.value"));
    }
    @Test void foreignSourceFieldsDoNotOverrideNativeHistoricalMeaning() throws Exception {
        var raw = TagParser.parseTag("{stat:'minecraft:jump',value:12,count:99,mode:'foreign',format:'foreign',stat_type:'foreign'}");
        var config = new HashMap<String,String>();
        assertEquals("", FtbStatisticFields.apply(StatisticsConfig.ID, raw, config));
        assertEquals(12, StatisticsConfig.parse(config).count());
        for (String field : List.of("count", "mode", "format", "stat_type")) assertEquals(raw.get(field).toString(), config.get("ftb." + field));
    }
    @Test void bareAndNamespacedAliasesImportRoundTripWithReports() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}");
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{chapter_groups:[]}");
        Files.writeString(temporary.resolve("chapters/stats.snbt"), """
                {id:'1001', quests:[{id:'2001',tasks:[
                  {id:'3001',type:'stat',stat:'minecraft:jump',value:12},
                  {id:'3002',type:'ftbquests:stat',stat:'example:registered_counter',value:2147483647,optional_task:true,opaque:'keep'}
                ]}]}
                """);
        var result = new FtbV13Importer().importBook(temporary, "stats", "book");
        assertFalse(result.report().hasErrors(), result.report().toJson());
        var restored = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(result.book())).getAsJsonObject());
        var tasks = restored.quests().getFirst().tasks();
        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().allMatch(task -> task.typeId().equals(StatisticsConfig.ID)));
        assertEquals("12", tasks.getFirst().config().get("count"));
        assertTrue(tasks.getLast().optional());
        assertTrue(tasks.getLast().config().containsKey("opaque"));
        assertTrue(result.fieldConversions().stream().anyMatch(conversion -> conversion.sourceField().equals("stat/value")));
    }
}

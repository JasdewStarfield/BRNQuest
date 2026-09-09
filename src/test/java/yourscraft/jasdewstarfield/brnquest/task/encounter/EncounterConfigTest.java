package yourscraft.jasdewstarfield.brnquest.task.encounter;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class EncounterConfigTest {
    @Test void boundsAndUnknownFieldsArePreserved() {
        assertEquals(0,EncounterConfig.parse(Map.of(),true).duration());
        assertEquals(Long.MAX_VALUE,EncounterConfig.parse(Map.of("count",Long.toString(Long.MAX_VALUE)),false).count());
        for(var values:java.util.List.of(Map.of("distance","NaN"),Map.of("distance","65"),Map.of("duration","-1"),Map.of("duration","1201"),Map.of("target","bad id")))
            assertThrows(IllegalArgumentException.class,()->EncounterConfig.parse(values,true));
        assertThrows(IllegalArgumentException.class,()->EncounterConfig.parse(Map.of("count","0"),false));
        assertEquals("kept",EncounterConfig.normalize(Map.of("target"," #minecraft:logs ","extension","kept")).get("extension"));
        assertEquals("#minecraft:logs",EncounterConfig.normalize(Map.of("target"," #minecraft:logs ")).get("target"));
        assertTrue(EncounterConfig.fields(true).stream().filter(f->f.key().equals("target")).findFirst().orElseThrow().serverSource().isPresent());
    }
    @Test void gazeRequiresConsecutiveTicksOnOneIdentity() {
        var timer=new GazeTimer();
        assertEquals(1,timer.sample(10,"a")); assertEquals(1,timer.sample(10,"a"));
        assertEquals(2,timer.sample(11,"a")); assertEquals(1,timer.sample(13,"a"));
        assertEquals(1,timer.sample(14,"b")); assertEquals(0,timer.sample(15,null));
        assertEquals(1,timer.sample(16,"b"));
    }
}

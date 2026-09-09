package yourscraft.jasdewstarfield.brnquest.compat.ftb;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.*;
import yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FtbEncounterFieldsTest {
    @Test void tagModesAndLegacyPriorityConvertWithoutDroppingConstraints() throws Exception {
        var raw=TagParser.parseTag("{observation_type:'block',observe_type:6,to_observe:'minecraft:undead',timer:0L}");
        var config=new HashMap<String,String>();
        assertEquals("",FtbEncounterFields.apply(EncounterConfig.OBSERVE,raw,config));
        assertEquals("entity",config.get("kind")); assertEquals("#minecraft:undead",config.get("target")); assertEquals("0",config.get("duration"));
        assertTrue(config.containsKey("ftb.observation_mode_conflict"));
        assertFalse(FtbEncounterFields.apply(EncounterConfig.OBSERVE,TagParser.parseTag("{observe_type:99}"),new HashMap<>()).isEmpty());
        var unsupported=new HashMap<String,String>();
        assertFalse(FtbEncounterFields.apply(EncounterConfig.OBSERVE,TagParser.parseTag("{observation_type:'block_state',to_observe:'minecraft:stone'}"),unsupported).isEmpty());
        assertThrows(IllegalArgumentException.class,()->EncounterConfig.parse(unsupported,true));
    }
    @Test void killTagOverridesIdAndLargeCountsRemainLong() throws Exception {
        var config=new HashMap<String,String>();
        assertEquals("",FtbEncounterFields.apply(EncounterConfig.KILL,TagParser.parseTag("{entity:'minecraft:pig',entityTypeTag:'minecraft:undead',value:4000000000L}"),config));
        assertEquals("#minecraft:undead",config.get("target")); assertEquals("4000000000",config.get("count"));
        assertFalse(FtbEncounterFields.apply(EncounterConfig.KILL,TagParser.parseTag("{custom_name:'named'}"),new HashMap<>()).isEmpty());
    }
}

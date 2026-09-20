package yourscraft.jasdewstarfield.brnquest.reward;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Publication and editor contracts do not need a running server or a random draw. */
class LootTableRewardTest {
    @Test void validatesSingleIdAndPreservesAuthorMetadata() {
        var type=new LootTableReward();
        for(String invalid:new String[]{"", "#minecraft:chests", "UPPER:invalid", "minecraft:a,minecraft:b"})
            assertTrue(StringMapConfigCodec.decode(type.configCodec(),Map.of("loot_table",invalid)).error().isPresent());
        var config=type.normalizeConfig(Map.of("loot_table"," minecraft:chests/simple_dungeon ","title","Prize","extension","keep"));
        assertEquals("minecraft:chests/simple_dungeon",config.get("loot_table"));
        assertEquals("keep",config.get("extension"));
        assertTrue(StringMapConfigCodec.decode(type.configCodec(),config).result().isPresent());
    }
    @Test void registeredForStandaloneAndCompositionWithRequiredPicker() {
        var type=RewardTypeRegistry.get(LootTableReward.ID);
        assertNotNull(type);assertTrue(type.claimHandler().isPresent());assertTrue(type.composition().isPresent());
        var field=type.configFields().stream().filter(f->f.key().equals("loot_table")).findFirst().orElseThrow();
        assertTrue(field.required());assertEquals(LootTableReward.ID,field.serverSource().orElseThrow());
        assertFalse(type.requiresManualClaim(Map.of("loot_table","minecraft:empty")));
    }
    @Test void oldCompositionAdaptersKeepPreparationAsDefaultFreeze() throws Exception {
        ComposableReward adapter=new ComposableReward() {
            public Map<String,String> prepare(RewardLeafContext context){return Map.of("old","prepared");}
            public RewardClaimResult execute(RewardLeafContext context,Map<String,String> prepared){throw new AssertionError("No effects during freeze");}
        };
        assertEquals(Map.of("old","prepared"),adapter.freeze(null));
    }
}

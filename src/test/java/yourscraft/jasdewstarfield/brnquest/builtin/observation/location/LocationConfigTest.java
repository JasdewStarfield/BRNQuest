package yourscraft.jasdewstarfield.brnquest.builtin.observation.location;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocationConfigTest {
    @Test void boxUsesHalfOpenIntegerBoundsAndAvoidsOverflow() {
        var box=LocationConfig.parse("location",Map.of("dimension","#test:worlds","position","-2,0,4","size","2,3,4"));
        assertTrue(box.contains(new BlockPos(-2,0,4))); assertTrue(box.contains(new BlockPos(-1,2,7)));
        assertFalse(box.contains(new BlockPos(0,0,4))); assertFalse(box.contains(new BlockPos(-2,3,4)));
        assertFalse(box.contains(new BlockPos(-2,0,8))); assertFalse(box.contains(new BlockPos(-3,0,4)));
        var extreme=LocationConfig.parse("location",Map.of("ignore_dimension","true","position","2147483647,0,0","size","2,1,1"));
        assertTrue(extreme.contains(new BlockPos(Integer.MAX_VALUE,0,0)));
    }
    @Test void malformedCoordinatesSelectorsAndSizeCannotComplete() {
        for(String size:List.of("0,1,1","-1,1,1","1,2","1,2,3,4","1,0.5,1"))
            assertThrows(IllegalArgumentException.class,()->LocationConfig.parse("location",Map.of("dimension","minecraft:overworld","size",size)));
        assertThrows(IllegalArgumentException.class,()->LocationConfig.parse("biome",Map.of("biome","#bad value")));
    }
    @Test void groupsResolveNestedMembersAndReportCyclesMissingEmptyAndDuplicates() {
        var a=ResourceLocation.parse("test:a");var b=ResourceLocation.parse("test:b");var world=ResourceLocation.parse("minecraft:overworld");
        assertEquals(Set.of(world),DimensionGroups.resolve("#test:a",Map.of(a,List.of("#test:b"),b,List.of(world.toString())),Set.of(world)).ids());
        for(var groups:List.of(Map.of(a,List.of("#test:a")),Map.of(a,List.of("#test:b")),Map.of(a,List.<String>of()),Map.of(a,List.of(world.toString(),world.toString())))) {
            var result=DimensionGroups.resolve("#test:a",groups,Set.of(world));assertTrue(result.ids().isEmpty());assertFalse(result.error().isBlank());
        }
        assertFalse(DimensionGroups.resolve("minecraft:the_nether",Map.of(),Set.of(world)).error().isBlank());
    }
}

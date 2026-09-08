package yourscraft.jasdewstarfield.brnquest.task.location;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pack stacks append low-to-high; a replacing pack can remove a lower pack's members. */
class DimensionGroupPackTest {
    @Test void stackedPacksAppendReplaceAndKeepMalformedGroupsUnresolved() {
        var world=ResourceLocation.parse("minecraft:overworld");
        var nether=ResourceLocation.parse("minecraft:the_nether");
        var key=ResourceLocation.parse("test:worlds");
        String low="{\"values\":[\"minecraft:overworld\"]}";
        String high="{\"values\":[\"minecraft:the_nether\"]}";
        assertEquals(Set.of(world,nether),DimensionGroups.resolve("#test:worlds",read(low,high),Set.of(world,nether)).ids());
        assertEquals(List.of(nether.toString()),read(low,high.replace("{","{\"replace\":true,")).get(key));
        assertFalse(DimensionGroups.resolve("#test:worlds",read("broken"),Set.of(world)).error().isEmpty());
    }
    private static Map<ResourceLocation,List<String>> read(String... documents) {
        var pack=(PackResources)Proxy.newProxyInstance(PackResources.class.getClassLoader(),new Class[]{PackResources.class},(proxy,method,args)->null);
        var resources=Arrays.stream(documents).map(json->new Resource(pack,()->new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))).toList();
        var manager=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class[]{ResourceManager.class},(proxy,method,args)-> {
            if(method.getName().equals("listResourceStacks")) return Map.of(ResourceLocation.parse("test:brnquest/target_groups/dimension/worlds.json"),resources);
            throw new UnsupportedOperationException(method.getName());
        });
        return DimensionGroups.read(manager);
    }
}

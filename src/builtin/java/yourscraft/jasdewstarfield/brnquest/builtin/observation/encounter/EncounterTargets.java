package yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import java.util.*;

/** Observe and kill share native registry selectors; tags remain live across datapack reloads. */
public final class EncounterTargets {
    public static final ResourceLocation OBSERVE_SOURCE=ResourceLocation.parse("brnquest:observe_targets");
    public static final ResourceLocation ENTITY_SOURCE=ResourceLocation.parse("brnquest:entity_targets");
    private EncounterTargets() {}
    public static void register(yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar registrar) {
        registrar.fieldSource(OBSERVE_SOURCE,new Source(true));
        registrar.fieldSource(ENTITY_SOURCE,new Source(false));
    }
    public static <T> boolean matches(Registry<T> registry, T value, String selector) {
        var id=ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector);
        if(id==null) return false;
        return selector.startsWith("#") ? registry.getTag(TagKey.create(registry.key(),id)).map(set -> set.stream().anyMatch(holder -> holder.value()==value)).orElse(false)
                : registry.containsKey(id) && registry.get(id)==value;
    }
    private static <T> int count(Registry<T> registry,String selector) {
        var id=ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector);
        return id==null ? 0 : selector.startsWith("#") ? registry.getTag(TagKey.create(registry.key(),id)).map(set->set.size()).orElse(0) : registry.containsKey(id) ? 1 : 0;
    }
    private record Source(boolean observe) implements ServerFieldSources.Source {
        public ServerFieldSources.Result query(ServerPlayer player,String filter,String selected) { return queryPage(player,filter,selected,0,Map.of()); }
        public ServerFieldSources.Result queryPage(ServerPlayer player,String filter,String selected,int offset,Map<String,String> context) {
            return observe && context.getOrDefault("kind","block").equals("block") ? page(BuiltInRegistries.BLOCK,filter,selected,offset) : page(BuiltInRegistries.ENTITY_TYPE,filter,selected,offset);
        }
    }
    private static <T> ServerFieldSources.Result page(Registry<T> registry,String filter,String selected,int offset) {
        var choices=new TreeSet<String>();
        registry.keySet().forEach(id->choices.add(id.toString())); registry.getTagNames().forEach(tag->choices.add("#"+tag.location()));
        var matching=choices.stream().filter(value->value.contains(filter.toLowerCase(Locale.ROOT))).toList();
        int count=count(registry,selected);
        return new ServerFieldSources.Result(matching.stream().skip(offset).limit(ServerFieldSources.PAGE_SIZE).map(value->new ServerFieldSources.Entry(value,count(registry,value))).toList(),
                matching.size(),count,selected.isBlank() || count>0 ? "" : "missing","");
    }
}

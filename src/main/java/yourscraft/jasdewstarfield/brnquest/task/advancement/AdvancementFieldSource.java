package yourscraft.jasdewstarfield.brnquest.task.advancement;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import java.util.*;

/** Live advancement IDs and BRNQuest groups use the existing paged picker contract. */
public final class AdvancementFieldSource implements ServerFieldSources.Source {
    public static final net.minecraft.resources.ResourceLocation CRITERIA = net.minecraft.resources.ResourceLocation.parse("brnquest:advancement_criteria");
    public static final net.minecraft.resources.ResourceLocation MEMBERS = net.minecraft.resources.ResourceLocation.parse("brnquest:advancement_members");
    /** Registration remains with the type; the picker only knows generic sources and previews. */
    public static void register() {
        ServerFieldSources.register(AdvancementConfig.ID,new AdvancementFieldSource());
        ServerFieldSources.register(CRITERIA,new Dependent(false));
        ServerFieldSources.register(MEMBERS,new Dependent(true));
    }
    private record Dependent(boolean members) implements ServerFieldSources.Source {
        public ServerFieldSources.Result query(ServerPlayer player, String filter, String selected) {
            return queryPage(player,filter,selected,0,Map.of());
        }
        public ServerFieldSources.Result queryPage(ServerPlayer player, String filter, String selected, int offset, Map<String,String> context) {
            String selector = members ? selected : context.getOrDefault("advancement", "");
            if (!members && (selector.isBlank() || selector.startsWith("#")))
                return new ServerFieldSources.Result(List.of(),0,0,"single_advancement","","");
            try {
                var holders = AdvancementTargets.resolve(player.server,new AdvancementConfig(selector,"",false));
                var values = members ? holders.stream().map(holder -> holder.id().toString()).toList()
                        : holders.getFirst().value().criteria().keySet().stream().sorted().toList();
                return page(values,filter,selected,offset,members);
            } catch (IllegalArgumentException error) {
                return new ServerFieldSources.Result(List.of(),0,0,"missing","",error.getMessage());
            }
        }
    }
    /** Both lists retain the shared pagination contract, including results beyond the first page. */
    static ServerFieldSources.Result page(List<String> values, String filter, String selected, int offset, boolean members) {
        var matching = values.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();
        return new ServerFieldSources.Result(matching.stream().skip(offset).limit(ServerFieldSources.PAGE_SIZE)
                .map(value -> new ServerFieldSources.Entry(value,1)).toList(), matching.size(),
                members ? values.size() : values.contains(selected) ? 1 : 0,"","");
    }
    public ServerFieldSources.Result query(ServerPlayer player, String filter, String selected) {
        return queryPage(player,filter,selected,0);
    }
    public ServerFieldSources.Result queryPage(ServerPlayer player, String filter, String selected, int offset) {
        var matching = AdvancementTargets.choices(player.server).stream().filter(value -> value.contains(filter.toLowerCase(Locale.ROOT))).toList();
        var entries = matching.stream().skip(offset).limit(ServerFieldSources.PAGE_SIZE)
                .map(value -> new ServerFieldSources.Entry(value, count(player,value))).toList();
        String detail = ""; int selectedCount = 0;
        if (!selected.isBlank()) {
            try { selectedCount = AdvancementTargets.resolve(player.server,new AdvancementConfig(selected,"",false)).size(); }
            catch (IllegalArgumentException error) { detail = error.getMessage(); }
        }
        return new ServerFieldSources.Result(entries,matching.size(),selectedCount,detail.isEmpty() ? "" : "missing","",detail, selected.startsWith("#") && detail.isEmpty() ? MEMBERS.toString() : "");
    }
    private int count(ServerPlayer player, String value) {
        try { return AdvancementTargets.resolve(player.server,new AdvancementConfig(value,"",false)).size(); }
        catch (IllegalArgumentException error) { return 0; }
    }
}

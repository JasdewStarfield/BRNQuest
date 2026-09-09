package yourscraft.jasdewstarfield.brnquest.task.advancement;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import java.util.*;

/** Live advancement IDs and BRNQuest groups use the existing paged picker contract. */
public final class AdvancementFieldSource implements ServerFieldSources.Source {
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
        return new ServerFieldSources.Result(entries,matching.size(),selectedCount,detail.isEmpty() ? "" : "missing","",detail);
    }
    private int count(ServerPlayer player, String value) {
        try { return AdvancementTargets.resolve(player.server,new AdvancementConfig(value,"",false)).size(); }
        catch (IllegalArgumentException error) { return 0; }
    }
}

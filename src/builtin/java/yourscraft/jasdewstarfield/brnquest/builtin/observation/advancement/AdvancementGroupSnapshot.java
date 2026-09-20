package yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import yourscraft.jasdewstarfield.brnquest.builtin.network.AdvancementGroupNetwork;
import java.util.*;

/** A new revision clears old worlds/reloads; partially received groups are never presented as complete. */
public final class AdvancementGroupSnapshot {
    private String revision = "";
    private final Map<String,List<String>> complete = new HashMap<>();
    private final Map<String,List<String>> pending = new HashMap<>();
    public void receive(AdvancementGroupNetwork.Page page) {
        if (page.reset()) { revision=page.revision(); complete.clear(); pending.clear(); return; }
        if (!revision.equals(page.revision())) return;
        if (!page.error().isEmpty()) { complete.put(page.group(),List.of()); pending.remove(page.group()); return; }
        var members = pending.computeIfAbsent(page.group(),ignored -> new ArrayList<>());
        if (page.offset()!=members.size() || page.offset()+page.members().size()>page.total()) return;
        members.addAll(page.members());
        if (members.size()==page.total()) { complete.put(page.group(),List.copyOf(members)); pending.remove(page.group()); }
    }
    public List<String> members(String group) { return complete.get(group); }
}

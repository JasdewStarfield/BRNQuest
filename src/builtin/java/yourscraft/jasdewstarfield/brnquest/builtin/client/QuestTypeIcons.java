package yourscraft.jasdewstarfield.brnquest.builtin.client;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import java.util.Set;

/** Editable GUI sprites for type choices; these remain decorative, never ingredients. */
public final class QuestTypeIcons {
    private QuestTypeIcons() {}
    private static final Set<String> BUILTINS = Set.of("checkmark", "item", "item_choice", "xp", "xp_levels", "command", "location", "observe", "kill", "advancement", "biome", "structure", "dimension", "custom", "reward_table", "loot_table");

    public static ResourceLocation sprite(ResourceLocation type) {
        // The registered encounter type is kill_entity; keep the existing editable kill.png asset.
        if (type.equals(yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig.KILL)) {
            return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/type/kill");
        }
        // Foreign types with matching paths must not inherit built-in semantics.
        String name = type.getNamespace().equals("brnquest") && BUILTINS.contains(type.getPath())
                ? type.getPath() : "unknown";
        return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/type/" + name);
    }

    private static final EditorIcon FALLBACK = ClientTypeIconFallback.icon();
    public static EditorIcon fallback() { return FALLBACK; }
    public static EditorIcon forType(ResourceLocation type) { return EditorIcon.sprite(sprite(type)); }
}

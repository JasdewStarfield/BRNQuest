package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import java.util.Set;

/** Editable GUI sprites for type choices; these remain decorative, never ingredients. */
final class QuestTypeIcons {
    private QuestTypeIcons() {}
    private static final Set<String> BUILTINS = Set.of("checkmark", "item", "item_choice", "xp", "xp_levels", "command", "location", "observe", "kill", "advancement", "biome", "structure", "dimension", "custom", "reward_table", "loot_table");

    static ResourceLocation sprite(ResourceLocation type) {
        // The registered encounter type is kill_entity; keep the existing editable kill.png asset.
        if (type.equals(yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.KILL)) {
            return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/type/kill");
        }
        // Foreign types with matching paths must not inherit built-in semantics.
        String name = type.getNamespace().equals("brnquest") && BUILTINS.contains(type.getPath())
                ? type.getPath() : "custom";
        return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/type/" + name);
    }

    private static final EditorIcon FALLBACK = EditorIcon.sprite(ResourceLocation.parse("brnquest:editor/type/custom"));
    static EditorIcon fallback() { return FALLBACK; }
    static EditorIcon forType(ResourceLocation type) { return EditorIcon.sprite(sprite(type)); }
}

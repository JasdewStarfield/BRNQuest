package yourscraft.jasdewstarfield.brnquest.builtin;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.extension.*;
import yourscraft.jasdewstarfield.brnquest.builtin.item.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.location.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.*;
/** Observation declarations share the public staged plugin registration and freeze boundary. */
public final class BuiltinObservationPlugin implements BrnQuestPlugin {
    public ResourceLocation id() { return ResourceLocation.parse("brnquest:builtin_observation"); }
    public void register(BrnQuestExtensionRegistrar registrar) {
        LocationFieldSources.register(registrar);
        AdvancementFieldSource.register(registrar);
        EncounterTargets.register(registrar);
        for (String kind : java.util.List.of("dimension", "biome", "location", "structure"))
            registrar.task(ResourceLocation.fromNamespaceAndPath("brnquest", kind), new LocationTask(kind));
        registrar.task(AdvancementConfig.ID, new AdvancementTask())
                .task(EncounterConfig.OBSERVE, new EncounterTask(true))
                .task(EncounterConfig.KILL, new EncounterTask(false));
        registrar.reward(AdvancementConfig.ID, new AdvancementReward());
    }
}

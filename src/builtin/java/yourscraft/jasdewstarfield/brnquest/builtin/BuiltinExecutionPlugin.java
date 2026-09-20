package yourscraft.jasdewstarfield.brnquest.builtin;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.extension.*;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.builtin.item.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.location.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.*;
/** Execution declarations share the public staged plugin registration and freeze boundary. */
public final class BuiltinExecutionPlugin implements BrnQuestPlugin {
    public ResourceLocation id() { return ResourceLocation.parse("brnquest:builtin_execution"); }
    public void register(BrnQuestExtensionRegistrar registrar) {
        LootTableReward.registerFieldSource(registrar);
        registrar.reward(RewardTypes.COMMAND, new CommandReward()).reward(LootTableReward.ID, new LootTableReward());
    }
}

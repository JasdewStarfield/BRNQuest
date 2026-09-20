package yourscraft.jasdewstarfield.brnquest.builtin;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.extension.*;
import yourscraft.jasdewstarfield.brnquest.builtin.item.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.location.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.*;
/** Composition declarations share the public staged plugin registration and freeze boundary. */
public final class BuiltinCompositionPlugin implements BrnQuestPlugin {
    public ResourceLocation id() { return ResourceLocation.parse("brnquest:builtin_composition"); }
    public void register(BrnQuestExtensionRegistrar registrar) {
        registrar.reward(RewardTableReward.ID, new RewardTableReward());
    }
}

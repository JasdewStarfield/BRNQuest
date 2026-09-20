package yourscraft.jasdewstarfield.brnquest.builtin;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.extension.*;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.builtin.item.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.location.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.*;
/** Item declarations share the public staged plugin registration and freeze boundary. */
public final class BuiltinItemPlugin implements BrnQuestPlugin {
    public ResourceLocation id() { return ResourceLocation.parse("brnquest:builtin_item"); }
    public void register(BrnQuestExtensionRegistrar registrar) {
        registrar.task(TaskTypes.ITEM, new UnifiedItemTask()).task(TaskTypes.ITEM_CHOICE, new UnifiedItemTask());
        registrar.reward(RewardTypes.ITEM, new ItemReward());
    }
}

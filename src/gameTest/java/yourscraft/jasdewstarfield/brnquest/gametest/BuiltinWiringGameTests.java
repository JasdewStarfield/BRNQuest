package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import java.util.List;

/** Uses the real mod lifecycle so a missing plugin or command subscriber cannot hide behind unit bootstrap. */
@GameTestHolder("brnquest")
public final class BuiltinWiringGameTests {
    @GameTest(template = "empty", batch = "builtinWiring")
    @PrefixGameTestTemplate(false)
    public static void allHistoricalTypesAndRecoveryCommandsAreRegistered(GameTestHelper helper) {
        for (String type : List.of("checkmark", "custom", "xp", "item", "item_choice", "dimension",
                "biome", "location", "structure", "advancement", "observe", "kill_entity")) {
            helper.assertTrue(TaskTypeRegistry.get(ResourceLocation.parse("brnquest:" + type)) != null,
                    "Missing historical task " + type);
        }
        for (String type : List.of("custom", "xp", "xp_levels", "item", "advancement", "command", "loot_table", "reward_table")) {
            helper.assertTrue(RewardTypeRegistry.get(ResourceLocation.parse("brnquest:" + type)) != null,
                    "Missing historical reward " + type);
        }
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("brnquest");
        helper.assertTrue(root != null, "Core command root exists");
        for (String command : List.of("command_reward", "reward_table")) {
            var node = root.getChild(command);
            helper.assertTrue(node != null && node.getChild("status") != null && node.getChild("acknowledge") != null,
                    "Recovery subcommands merge into core root: " + command);
        }
        helper.succeed();
    }
}

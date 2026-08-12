package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.List;
import java.util.Map;

/** In-game transaction checks that need real registries, inventories, and SavedData. */
@GameTestHolder(BRNQuest.MOD_ID)
@SuppressWarnings("removal")
public final class BrnQuestGameTests {
    private BrnQuestGameTests() {}

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemSubmissionConsumesExactRequiredCount(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ResourceLocation taskId = id("item_task");
        TaskDefinition task = new TaskDefinition(id("book"), taskId, id("item"),
                Map.of("item", "{count:1,id:\"minecraft:stone\"}", "count", "2L", "consume_items", "1b"), false);
        QuestDefinition quest = quest("item_quest", List.of(), List.of(task), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.STONE, 2));
        ProgressEngine.get().reconcile(player);

        var result = ProgressEngine.get().complete(player, quest.id(), false);

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0, "submitted items must be consumed exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dependencyUnlocksAfterCheckmarkCompletion(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        QuestDefinition first = quest("first", List.of(), List.of(new TaskDefinition(id("book"), id("check"), id("checkmark"), Map.of(), false)), List.of());
        QuestDefinition second = quest("second", List.of(first.id()), List.of(), List.of());
        install(first, second);
        ProgressEngine.get().reconcile(player);

        var result = ProgressEngine.get().complete(player, first.id(), true);

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(second.id().toString()), QuestStatus.AVAILABLE,
                "dependent quest must unlock in the same server transaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void individualObjectiveClicksPreserveSiblingProgress(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition first = new TaskDefinition(id("book"), id("check_one"), id("checkmark"), Map.of(), false);
        TaskDefinition second = new TaskDefinition(id("book"), id("check_two"), id("checkmark"), Map.of(), false);
        QuestDefinition quest = quest("multi_check", List.of(), List.of(first, second), List.of());
        install(quest);
        ProgressEngine.get().reconcile(player);

        var partial = ProgressEngine.get().completeTask(player, quest.id(), first.id());
        helper.assertTrue(!partial.success(), "one of two required rows must not complete the quest");
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(first.id().toString()), 1L,
                "the clicked row must retain its progress");
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(quest.id().toString()), QuestStatus.AVAILABLE,
                "quest remains available while a sibling row is incomplete");

        var completed = ProgressEngine.get().completeTask(player, quest.id(), second.id());
        helper.assertTrue(completed.success(), completed.message());
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(quest.id().toString()), QuestStatus.COMPLETED,
                "the final row click must complete the quest transaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void submittedItemRowIsNotConsumedTwice(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition item = new TaskDefinition(id("book"), id("submitted_item"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:stone\"}", "count", "2L", "consume_items", "1b"), false);
        TaskDefinition check = new TaskDefinition(id("book"), id("later_check"), id("checkmark"), Map.of(), false);
        QuestDefinition quest = quest("staged_submission", List.of(), List.of(item, check), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.STONE, 2));
        ProgressEngine.get().reconcile(player);

        ProgressEngine.get().completeTask(player, quest.id(), item.id());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0, "clicked item row consumes once");
        var completed = ProgressEngine.get().completeTask(player, quest.id(), check.id());

        helper.assertTrue(completed.success(), completed.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0, "final completion must not consume the submitted row again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void duplicateRewardClaimIsIdempotent(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        RewardDefinition reward = new RewardDefinition(id("book"), id("diamond_reward"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:diamond\"}"), "manual", false);
        QuestDefinition quest = quest("reward_quest", List.of(), List.of(), List.of(reward));
        install(quest);
        ProgressEngine.get().forceComplete(player, quest.id());

        var first = ProgressEngine.get().claim(player, reward.id());
        var second = ProgressEngine.get().claim(player, reward.id());

        helper.assertTrue(first.success() && second.success(), "duplicate claim should be a successful no-op");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 1, "reward side effect must run once");
        helper.succeed();
    }

    private static QuestDefinition quest(String path, List<ResourceLocation> dependencies,
                                         List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id(path), id("chapter"), path, "", "", "", 0, 0,
                dependencies, tasks, rewards, path.toUpperCase(java.util.Locale.ROOT));
    }

    private static void install(QuestDefinition... quests) {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation chapterId = id("chapter");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "GameTest",
                List.of(new ChapterGroupDefinition(bookId, groupId, "GameTest", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "GameTest", "", 0, List.of(quests))), Map.of());
        if (!QuestBookManager.get().install(book, new DiagnosticReport())) throw new IllegalStateException("GameTest book failed validation");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, path); }
}

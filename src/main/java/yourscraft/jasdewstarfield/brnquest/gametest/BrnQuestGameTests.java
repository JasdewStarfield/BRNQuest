package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationContext;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    public static void identicalConsumingRowsCannotOverspendInventory(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition first = consumingStoneTask("first_consumer", 4);
        TaskDefinition second = consumingStoneTask("second_consumer", 4);
        QuestDefinition quest = quest("shared_inventory_budget", List.of(), List.of(first, second), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.STONE, 6));
        ProgressEngine.get().reconcile(player);

        var firstResult = ProgressEngine.get().completeTask(player, quest.id(), first.id());
        var secondResult = ProgressEngine.get().completeTask(player, quest.id(), second.id());

        helper.assertTrue(!firstResult.success(), "the first row remains partial while its sibling is incomplete");
        helper.assertTrue(!secondResult.success(), "the second row must be rejected after the first spends four items");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2,
                "two consuming rows must share the authoritative server inventory budget");
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(first.id().toString()), 1L,
                "the accepted first row must remain submitted");
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(second.id().toString()), 0L,
                "the rejected second row must not gain progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void repeatedSubmissionOfSameRowConsumesOnlyOnce(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition item = consumingStoneTask("idempotent_consumer", 4);
        TaskDefinition check = new TaskDefinition(id("book"), id("pending_check"), id("checkmark"), Map.of(), false);
        QuestDefinition quest = quest("duplicate_submission", List.of(), List.of(item, check), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.STONE, 6));
        ProgressEngine.get().reconcile(player);

        ProgressEngine.get().completeTask(player, quest.id(), item.id());
        var duplicate = ProgressEngine.get().completeTask(player, quest.id(), item.id());

        helper.assertTrue(duplicate.success(), "a duplicate task command must be an idempotent successful no-op");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2,
                "repeating one submitted row must not consume its items twice");
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(item.id().toString()), 1L,
                "duplicate delivery must not increment the task ledger twice");
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

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void partialInventoryFitDropsOnlyRewardRemainder(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        // Fill every main slot, leaving room for exactly one of the two rewarded diamonds.
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 63));
        RewardDefinition reward = new RewardDefinition(id("book"), id("partial_reward"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:diamond\"}", "count", "2"), "manual", false);
        QuestDefinition quest = quest("partial_reward_quest", List.of(), List.of(), List.of(reward));
        install(quest);
        ProgressEngine.get().forceComplete(player, quest.id());
        Set<java.util.UUID> existingItems = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(player.blockPosition()).inflate(8))
                .stream().map(ItemEntity::getUUID).collect(Collectors.toSet());

        var result = ProgressEngine.get().claim(player, reward.id());

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 64,
                "inventory must accept the one available diamond");
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(player.blockPosition()).inflate(8), entity -> entity.getItem().is(Items.DIAMOND)
                                && !existingItems.contains(entity.getUUID()))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertValueEqual(dropped, 1, "the uninserted reward remainder must become an item entity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void onlinePlayersReconcileAfterTaskBookRevisionChanges(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        QuestDefinition original = quest("original", List.of(), List.of(), List.of());
        install(original);
        ProgressEngine.get().reconcile(player);

        QuestDefinition added = quest("added_after_reload", List.of(), List.of(), List.of());
        install(original, added);
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(added.id().toString()), QuestStatus.LOCKED,
                "new definitions start absent from an old player progress map");

        ProgressEngine.get().reconcileOnlinePlayers(helper.getLevel().getServer());

        helper.assertValueEqual(ProgressEngine.get().progress(player).status(added.id().toString()), QuestStatus.AVAILABLE,
                "online reload reconciliation must make an unblocked new quest immediately available");
        helper.assertValueEqual(ProgressEngine.get().progress(player).revision(),
                QuestBookManager.get().active().orElseThrow().revision(),
                "online progress revision must match the installed task book");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void selfContextCannotModifyAnotherPlayer(GameTestHelper helper) {
        var actor = helper.makeMockServerPlayerInLevel();
        var target = helper.makeMockServerPlayerInLevel();
        QuestDefinition quest = quest("permission_boundary", List.of(), List.of(), List.of());
        install(quest);
        ProgressEngine.get().reconcile(target);

        var denied = BrnQuestApi.completeQuestResult(OperationContext.self(actor), target, quest.id().toString());

        helper.assertTrue(!denied.success() && denied.code().equals("FORBIDDEN"),
                "self authority must not cross a player UUID boundary");
        helper.assertValueEqual(ProgressEngine.get().progress(target).status(quest.id().toString()),
                QuestStatus.AVAILABLE, "forbidden operation must not mutate target progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void completionPublishesImmutableObservationEvents(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition task = new TaskDefinition(id("book"), id("event_check"), id("checkmark"), Map.of(), false);
        QuestDefinition quest = quest("event_quest", List.of(), List.of(task), List.of());
        install(quest);
        ProgressEngine.get().reconcile(player);
        AtomicInteger taskEvents = new AtomicInteger();
        AtomicInteger questEvents = new AtomicInteger();

        try (var taskSubscription = BrnQuestEvents.subscribe(TaskProgressChangedEvent.class, event -> {
            if (event.playerId().equals(player.getUUID()) && event.taskId().equals(task.id())) taskEvents.incrementAndGet();
        }); var questSubscription = BrnQuestEvents.subscribe(QuestCompletedEvent.class, event -> {
            if (event.playerId().equals(player.getUUID()) && event.questId().equals(quest.id())) questEvents.incrementAndGet();
        })) {
            BrnQuestApi.submitQuestCompletionResult(OperationContext.self(player), player, quest.id().toString(), true);
        }

        helper.assertValueEqual(taskEvents.get(), 1, "task progress change must publish once");
        helper.assertValueEqual(questEvents.get(), 1, "quest completion must publish once");
        helper.succeed();
    }

    private static QuestDefinition quest(String path, List<ResourceLocation> dependencies,
                                         List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id(path), id("chapter"), path, "", "", "", 0, 0,
                dependencies, tasks, rewards, path.toUpperCase(java.util.Locale.ROOT));
    }

    private static TaskDefinition consumingStoneTask(String path, int count) {
        return new TaskDefinition(id("book"), id(path), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:stone\"}", "count", Integer.toString(count),
                        "consume_items", "1b"), false);
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

package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.OperationContext;
import yourscraft.jasdewstarfield.brnquest.author.AuthorOperationResult;
import yourscraft.jasdewstarfield.brnquest.author.AuthorBackupService;
import yourscraft.jasdewstarfield.brnquest.author.BackupKind;
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.author.DraftRepository;
import yourscraft.jasdewstarfield.brnquest.author.DraftSnapshot;
import yourscraft.jasdewstarfield.brnquest.author.DraftService;
import yourscraft.jasdewstarfield.brnquest.author.DraftEditService;
import yourscraft.jasdewstarfield.brnquest.author.DraftDiffService;
import yourscraft.jasdewstarfield.brnquest.author.DraftPersistenceService;
import yourscraft.jasdewstarfield.brnquest.author.DraftPublishService;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionService;
import yourscraft.jasdewstarfield.brnquest.compat.ftb.FtbImportService;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviders;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviderRegistry;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.runtime.ExtensionRegistrationLifecycle;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

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

    @GameTest(template = "empty", batch = "externalRewardBoundary")
    @PrefixGameTestTemplate(false)
    public static void externalRewardOwnsNormalizationAndClaimPolicy(GameTestHelper helper) {
        if (!ModList.get().isLoaded("brnquest_example")) { helper.succeed(); return; }
        var player = helper.makeMockServerPlayerInLevel();
        var typeId = ResourceLocation.parse("brnquest_example:guarded_tag");
        var raw = new RewardDefinition(id("book"), id("guarded_tag_reward"), typeId,
                Map.of("tag", "  granted_tag  ", "opaque", "keep"), "manual", false);
        var quest = quest("external_guarded", List.of(), List.of(), List.of());
        install(quest);
        var book = QuestBookManager.get().active().orElseThrow().book();
        // Exercise the author mutation boundary, not merely the extension method in isolation.
        var changed = yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor.addReward(book, quest.id(), raw);
        var updatedBook = changed.value().book();
        var updatedQuest = updatedBook.quests().stream().filter(q -> q.id().equals(quest.id())).findFirst().orElseThrow();
        var reward = updatedQuest.rewards().getFirst();
        helper.assertTrue(reward.config().get("tag").equals("granted_tag") && reward.config().get("opaque").equals("keep"),
                "external normalization and opaque data survive author writes");
        var schema = ConfigEditorSchemas.forReward(ApiViews.reward(reward));
        helper.assertTrue(schema.fields().get(1).valueLabelKeys().get("player").equals("screen.brnquest_example.mode.player"),
                "external enum metadata is isolated from built-in command labels");
        install(updatedQuest);
        var engine = ProgressEngine.get();
        player.addTag("brnquest_example_block");
        helper.assertTrue(!engine.claim(player, reward.id()).success(), "incomplete quest remains protected");
        engine.progress(player).status(quest.id().toString(), yourscraft.jasdewstarfield.brnquest.progress.QuestStatus.COMPLETED);
        helper.assertTrue(!engine.claim(player, reward.id()).success() && !engine.rewardClaimed(player, reward), "failure does not consume claim");
        player.removeTag("brnquest_example_block"); player.addTag("brnquest_example_wait");
        var pending = engine.claim(player, reward.id());
        helper.assertTrue(pending.success() && !pending.changed() && !engine.rewardClaimed(player, reward), "pending does not consume claim");
        player.removeTag("brnquest_example_wait");
        helper.assertTrue(engine.claim(player, reward.id()).changed() && player.getTags().contains("granted_tag"), "external handler grants and commits");
        helper.assertTrue(!engine.claim(player, reward.id()).changed(), "duplicate claim stays closed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void publicApiExampleAddonCompletesItsFullContract(GameTestHelper helper) {
        // The same main test suite also runs with -PexcludeExampleAddon to prove the pure-core
        // combination. In that mode there is intentionally no companion contract to execute.
        if (!ModList.get().isLoaded("brnquest_example")) {
            helper.succeed();
            return;
        }
        var player = helper.makeMockServerPlayerInLevel();
        ResourceLocation passiveId = ResourceLocation.fromNamespaceAndPath("brnquest_example", "marker");
        ResourceLocation submittedId = ResourceLocation.fromNamespaceAndPath("brnquest_example", "signal");
        ResourceLocation rewardTypeId = ResourceLocation.fromNamespaceAndPath("brnquest_example", "experience");
        TaskDefinition passive = new TaskDefinition(id("book"), id("example_marker"), passiveId,
                Map.of("tag", "brnquest_example_ready"), false);
        TaskDefinition submitted = new TaskDefinition(id("book"), id("example_signal"), submittedId,
                Map.of("title", "Send integration signal"), false);
        RewardDefinition reward = new RewardDefinition(id("book"), id("example_experience"), rewardTypeId,
                Map.of("amount", "3"), "manual", false);
        QuestDefinition quest = quest("example_addon", List.of(), List.of(passive, submitted), List.of(reward));
        install(quest);
        // This external server state is observed passively; it does not call a BRNQuest
        // progress mutation API and is deterministic for headless mock players.
        player.addTag("brnquest_example_ready");
        ProgressEngine.get().reconcile(player);

        helper.assertTrue(TaskTypeRegistry.get(passiveId) != null && TaskTypeRegistry.get(submittedId) != null,
                "example add-on task types must register through the public registry");
        helper.assertTrue(RewardTypeRegistry.get(rewardTypeId) != null,
                "example add-on reward type must register through the public registry");
        helper.assertTrue(!ConfigEditorSchemas.forTask(ApiViews.task(passive)).rawFallback(),
                "example passive task must publish editor field metadata");

        var initial = BrnQuestApi.submitQuestCompletionResult(OperationContext.self(player), player,
                quest.id().toString(), false);
        helper.assertTrue(!initial.success(), "passive condition alone must not complete the submitted objective");
        var completed = BrnQuestApi.completeTaskResult(OperationContext.self(player), player,
                quest.id().toString(), submitted.id().toString());
        helper.assertTrue(completed.success(), completed.message());
        helper.assertTrue(player.getTags().contains("brnquest_example_observed"),
                "example read-only event subscriber must observe completion");

        int experienceBefore = player.totalExperience;
        var firstClaim = BrnQuestApi.claimRewardResult(OperationContext.self(player), player, reward.id().toString());
        var repeatedClaim = BrnQuestApi.claimRewardResult(OperationContext.self(player), player, reward.id().toString());
        helper.assertTrue(firstClaim.changed(), firstClaim.message());
        helper.assertTrue(repeatedClaim.success() && !repeatedClaim.changed(),
                "example reward must remain idempotent through the public operation API");
        helper.assertValueEqual(player.totalExperience - experienceBefore, 3,
                "example reward side effect must execute exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dedicatedServerFreezesCommonAndScriptRegistrationBeforeReload(GameTestHelper helper) {
        var state = ExtensionRegistrationLifecycle.state();
        helper.assertTrue(state.commonFrozen() && state.scriptFrozen(),
                "common and reserved script registration must close before server reload");
        helper.assertTrue(!state.clientFrozen(),
                "dedicated server must not execute the client registration lifecycle");
        helper.assertTrue(BrnQuestPlugins.isFrozen() && TaskTypeRegistry.isFrozen() && RewardTypeRegistry.isFrozen()
                        && ProgressOwnerProviderRegistry.isFrozen(),
                "the plugin facade and all common extension registries must share the lifecycle freeze");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void personalOwnerUsesStablePlayerIdentity(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        QuestDefinition quest = quest("personal_owner", List.of(), List.of(), List.of());
        install(quest);
        ProgressEngine.get().reconcile(player);
        var owner = BrnQuestApi.getProgressOwner(player).orElseThrow();

        helper.assertValueEqual(owner.id().providerId(), ProgressOwnerProviders.PERSONAL,
                "stage 3 must activate only the personal owner provider");
        helper.assertValueEqual(owner.id().ownerId(), player.getUUID(),
                "personal owner ID must equal the durable player UUID");
        helper.assertValueEqual(owner.members(), Set.of(player.getUUID()),
                "personal owner membership must contain only the player");
        helper.assertValueEqual(owner.lifecycle(), ProgressOwnerLifecycle.ACTIVE,
                "personal owners must remain active instead of producing synthetic archives");
        helper.assertValueEqual(BrnQuestApi.getProgress(player, quest.id().toString()).orElseThrow().owner(), owner.id(),
                "public progress projections must identify their authoritative owner");
        helper.succeed();
    }

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

        // Quest-wide checks cannot bypass the explicit item submission/slot-selection boundary.
        var premature = ProgressEngine.get().complete(player, quest.id(), false);
        helper.assertTrue(!premature.success(), "inventory readiness alone must not finish the quest");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2, "a check must not consume items");
        var result = ProgressEngine.get().completeTask(player, quest.id(), task.id());

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0, "submitted items must be consumed exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemChoiceListConsumesFirstSatisfiedCandidatesInAuthorOrder(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ResourceLocation taskId = id("item_choice_list");
        String matcher = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\","
                + "\"{count:1,id:\\\"minecraft:dirt\\\"}\","
                + "\"{count:1,id:\\\"minecraft:diamond\\\"}\"],\"required\":2}";
        TaskDefinition task = new TaskDefinition(id("book"), taskId, id("item_choice"),
                Map.of("matcher", matcher, "count", "2", "consume_items", "true"), false);
        QuestDefinition quest = quest("item_choice_list_quest", List.of(), List.of(task), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.STONE, 2));
        player.getInventory().add(new ItemStack(Items.DIRT, 2));
        player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        ProgressEngine.get().reconcile(player);

        var result = ProgressEngine.get().completeTask(player, quest.id(), task.id());

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0,
                "the first satisfied author candidate must be consumed");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 0,
                "the second satisfied author candidate must be consumed");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 2,
                "satisfied candidates beyond required must remain untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemChoiceListConsumesOnlyPlayerSelectedCandidates(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ResourceLocation taskId = id("item_choice_selected_list");
        String matcher = "{\"version\":2,\"entries\":["
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:stone\\\"}\",\"count\":2},"
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:dirt\\\"}\",\"count\":2},"
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:diamond\\\"}\",\"count\":2}],"
                + "\"required\":2}";
        TaskDefinition task = new TaskDefinition(id("book"), taskId, id("item"),
                Map.of("matcher", matcher, "required_entries", "2", "consume_items", "true"), false);
        QuestDefinition quest = quest("item_choice_selected_list_quest", List.of(), List.of(task), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.STONE, 2));
        player.getInventory().add(new ItemStack(Items.DIRT, 2));
        player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        ProgressEngine.get().reconcile(player);

        var result = ProgressEngine.get().completeTask(player, quest.id(), task.id(),
                new yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection(List.of(1, 2)));

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2,
                "an unselected satisfied entry must remain untouched");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 0,
                "the first player-selected entry must be consumed");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0,
                "the second player-selected entry must be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemSubmissionConsumesChosenInventorySlotWithoutComponents(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ResourceLocation taskId = id("item_selected_component_stack");
        String matcher = "{\"version\":2,\"entries\":["
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:diamond_sword\\\"}\","
                + "\"count\":1}],\"required\":1}";
        TaskDefinition task = new TaskDefinition(id("book"), taskId, id("item"),
                Map.of("matcher", matcher, "required_entries", "1", "consume_items", "true"), false);
        QuestDefinition quest = quest("item_selected_component_stack_quest", List.of(), List.of(task), List.of());
        install(quest);
        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        enchantedSword.set(DataComponents.CUSTOM_NAME, Component.literal("Valuable sword"));
        player.getInventory().setItem(0, enchantedSword);
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        ProgressEngine.get().reconcile(player);

        var result = ProgressEngine.get().completeTask(player, quest.id(), task.id(),
                new yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection(List.of(1)));

        helper.assertTrue(result.success(), result.message());
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1,
                "the unselected component-bearing sword must remain in its slot");
        helper.assertTrue(player.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME),
                "the remaining sword must keep its components");
        helper.assertTrue(player.getInventory().getItem(1).isEmpty(),
                "the selected plain sword must be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemChoiceTagRequiresOneItemTypeAndDoesNotMixTagMembers(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ResourceLocation taskId = id("item_choice_tag");
        String matcher = "{\"mode\":\"tag\",\"tag\":\"minecraft:planks\"}";
        TaskDefinition task = new TaskDefinition(id("book"), taskId, id("item_choice"),
                Map.of("matcher", matcher, "count", "2", "consume_items", "true"), false);
        QuestDefinition quest = quest("item_choice_tag_quest", List.of(), List.of(task), List.of());
        install(quest);
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS));
        player.getInventory().add(new ItemStack(Items.BIRCH_PLANKS));
        ProgressEngine.get().reconcile(player);

        var mixed = ProgressEngine.get().completeTask(player, quest.id(), task.id());
        helper.assertTrue(!mixed.success(), "different tag members must not be combined into one required stack");
        helper.assertValueEqual(player.getInventory().countItem(Items.OAK_PLANKS), 1,
                "a rejected submission must not consume the first tag member");
        helper.assertValueEqual(player.getInventory().countItem(Items.BIRCH_PLANKS), 1,
                "a rejected submission must not consume the second tag member");

        player.getInventory().add(new ItemStack(Items.OAK_PLANKS));
        var accepted = ProgressEngine.get().completeTask(player, quest.id(), task.id());
        helper.assertTrue(accepted.success(), accepted.message());
        helper.assertValueEqual(player.getInventory().countItem(Items.OAK_PLANKS), 0,
                "the first qualifying inventory item type must supply the complete tag requirement");
        helper.assertValueEqual(player.getInventory().countItem(Items.BIRCH_PLANKS), 1,
                "other tag members must remain untouched");
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
    public static void dependencyRelocksAfterPrerequisiteReset(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition prerequisiteTask = new TaskDefinition(id("book"), id("reset_prerequisite_check"),
                TaskTypes.CHECKMARK, Map.of(), false);
        QuestDefinition prerequisite = quest("reset_prerequisite", List.of(), List.of(prerequisiteTask), List.of());
        QuestDefinition dependent = quest("reset_dependent", List.of(prerequisite.id()), List.of(), List.of());
        install(prerequisite, dependent);
        ProgressEngine.get().reconcile(player);
        ProgressEngine.get().completeTask(player, prerequisite.id(), prerequisiteTask.id());
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(dependent.id().toString()),
                QuestStatus.AVAILABLE, "the completed prerequisite must unlock its dependent");

        ProgressEngine.get().reset(player, prerequisite.id());

        helper.assertValueEqual(ProgressEngine.get().progress(player).status(dependent.id().toString()),
                QuestStatus.LOCKED, "resetting a prerequisite must relock its dependent in the same reconciliation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void sequentialObjectivesRejectOutOfOrderSubmission(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition first = new TaskDefinition(id("book"), id("sequential_first"), TaskTypes.CHECKMARK,
                Map.of(), false);
        TaskDefinition second = new TaskDefinition(id("book"), id("sequential_second"), TaskTypes.CHECKMARK,
                Map.of(), false);
        QuestBehavior behavior = new QuestBehavior(false, false, false, 0, false, false, false,
                DependencyRequirement.ALL_COMPLETED, 0, true, false, 0, false);
        QuestDefinition quest = quest("sequential", List.of(), List.of(first, second), List.of(), behavior);
        install(quest);
        ProgressEngine.get().reconcile(player);

        var rejected = ProgressEngine.get().completeTask(player, quest.id(), second.id());
        helper.assertValueEqual(rejected.code(), "OUT_OF_SEQUENCE",
                "a later required objective must remain server-locked");
        ProgressEngine.get().complete(player, quest.id(), true);
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(first.id().toString()), 1L,
                "the first objective must retain its receipt while the quest remains incomplete");
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(second.id().toString()), 0L,
                "one quest-wide intent must not skip through every sequential objective");
        helper.assertTrue(ProgressEngine.get().completeTask(player, quest.id(), second.id()).success(),
                "the next objective must unlock after the first receipt is stored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void craftingOnlyItemObjectiveCountsCraftedOutput(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition task = new TaskDefinition(id("book"), id("crafted_stone"), TaskTypes.ITEM,
                Map.of("item", "{count:1,id:\"minecraft:stone\"}", "count", "3",
                        "only_from_crafting", "true"), false);
        QuestDefinition quest = quest("crafting_only", List.of(), List.of(task), List.of());
        install(quest);
        ProgressEngine.get().reconcile(player);

        var manual = ProgressEngine.get().completeTask(player, quest.id(), task.id());
        helper.assertValueEqual(manual.code(), "NOT_SUBMITTABLE",
                "crafting-only objectives must reject inventory submission");
        ProgressEngine.get().recordCraft(player, new ItemStack(Items.STONE, 2));
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(task.id().toString()), 2L,
                "the server must count the actual crafted stack size");
        ProgressEngine.get().recordCraft(player, new ItemStack(Items.STONE, 1));
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(quest.id().toString()),
                QuestStatus.COMPLETED, "the required crafted output must complete the objective");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void experienceTaskAndRewardsMutatePlayerOnce(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.giveExperiencePoints(30);
        int initialPoints = player.totalExperience;
        TaskDefinition task = new TaskDefinition(id("book"), id("xp_cost"), TaskTypes.XP,
                Map.of("value", "5", "points", "true"), false);
        RewardDefinition points = new RewardDefinition(id("book"), id("xp_points_reward"), RewardTypes.XP,
                Map.of("xp", "7"), "manual", false);
        RewardDefinition levels = new RewardDefinition(id("book"), id("xp_levels_reward"), RewardTypes.XP_LEVELS,
                Map.of("xp_levels", "2"), "manual", false);
        QuestDefinition quest = quest("experience", List.of(), List.of(task), List.of(points, levels));
        install(quest);
        ProgressEngine.get().reconcile(player);

        helper.assertTrue(ProgressEngine.get().completeTask(player, quest.id(), task.id()).success(),
                "the experience objective must submit when enough points are present");
        helper.assertValueEqual(player.totalExperience, initialPoints - 5,
                "the objective must deduct its raw point cost exactly once");
        helper.assertTrue(ProgressEngine.get().claim(player, points.id()).changed(),
                "the points reward must claim once");
        helper.assertValueEqual(player.totalExperience, initialPoints + 2,
                "the points reward must add seven after the five-point cost");
        int levelBeforeReward = player.experienceLevel;
        helper.assertTrue(ProgressEngine.get().claim(player, levels.id()).changed(),
                "the levels reward must claim once");
        helper.assertValueEqual(player.experienceLevel, levelBeforeReward + 2,
                "the levels reward must add whole levels");
        int afterClaims = player.totalExperience;
        helper.assertTrue(!ProgressEngine.get().claim(player, points.id()).changed(),
                "a duplicate experience claim must be an idempotent no-op");
        helper.assertValueEqual(player.totalExperience, afterClaims,
                "a duplicate claim must not grant more experience");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void repeatCycleWaitsForManualRewardThenReopens(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        TaskDefinition task = new TaskDefinition(id("book"), id("repeat_check"), TaskTypes.CHECKMARK,
                Map.of(), false);
        RewardDefinition reward = new RewardDefinition(id("book"), id("repeat_reward"), RewardTypes.ITEM,
                Map.of("item", "{count:1,id:\"minecraft:diamond\"}"), "manual", false);
        QuestBehavior behavior = new QuestBehavior(false, false, false, 0, false, false, false,
                DependencyRequirement.ALL_COMPLETED, 0, false, true, 0, false);
        QuestDefinition quest = quest("repeatable", List.of(), List.of(task), List.of(reward), behavior);
        install(quest);
        ProgressEngine.get().reconcile(player);

        helper.assertTrue(ProgressEngine.get().completeTask(player, quest.id(), task.id()).success(),
                "the first repeat cycle must complete");
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(quest.id().toString()),
                QuestStatus.COMPLETED, "an unclaimed manual reward must block reopening");
        helper.assertTrue(ProgressEngine.get().claim(player, reward.id()).changed(),
                "claiming the blocking reward must succeed");
        helper.assertValueEqual(ProgressEngine.get().progress(player).status(quest.id().toString()),
                QuestStatus.AVAILABLE, "zero-cooldown repeat must reopen after its reward is claimed");
        helper.assertValueEqual(ProgressEngine.get().progress(player).taskProgress(task.id().toString()), 0L,
                "the next cycle must clear objective progress");
        helper.assertValueEqual(ProgressEngine.get().progress(player).completionCycles(quest.id().toString()), 1,
                "the completed cycle count must survive reopening");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 1,
                "the first cycle must deliver exactly one reward");
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
    public static void holdingObjectivesRequireSeparateReceiptsWithoutConsumingItems(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        Map<String, String> config = Map.of("item", "{count:1,id:\"minecraft:stone\"}", "count", "2",
                "consume_items", "false");
        TaskDefinition first = new TaskDefinition(id("book"), id("hold_first"), id("item"), config, false);
        TaskDefinition second = new TaskDefinition(id("book"), id("hold_second"), id("item_choice"), config, false);
        QuestDefinition quest = quest("separate_holding", List.of(), List.of(first, second), List.of());
        install(quest);
        engine.reconcile(player);
        player.getInventory().items.set(0, new ItemStack(Items.STONE, 2));

        helper.assertTrue(!engine.complete(player, quest.id(), false).success(), "inventory checks cannot auto-complete");
        engine.completeTask(player, quest.id(), first.id());
        helper.assertValueEqual(engine.progress(player).taskProgress(first.id().toString()), 1L, "first receipt stored");
        helper.assertValueEqual(engine.progress(player).taskProgress(second.id().toString()), 0L, "sibling remains unsubmitted");
        helper.assertValueEqual(engine.progress(player).status(quest.id().toString()), QuestStatus.AVAILABLE,
                "quest waits for the second click even when the same inventory satisfies both");
        helper.assertTrue(engine.completeTask(player, quest.id(), second.id()).success(), "second click completes");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2, "holding objectives never consume");
        helper.assertTrue(!engine.completeTask(player, quest.id(), first.id()).changed(), "replayed click is a no-op");
        helper.assertTrue(!TaskTypeExecutor.reevaluateOnInventoryChange(TaskTypeRegistry.get(first.typeId()), ApiViews.task(first)),
                "built-in holding tasks no longer opt into passive inventory completion");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void completingOneRowNeverConsumesUnsubmittedSiblingOrOptionalItems(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        TaskDefinition check = new TaskDefinition(id("book"), id("explicit_check"), id("checkmark"), Map.of(), false);
        TaskDefinition item = consumingStoneTask("explicit_stone", 2);
        TaskDefinition optional = new TaskDefinition(id("book"), id("optional_dirt"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:dirt\"}", "count", "1", "consume_items", "true"), true);
        QuestDefinition quest = quest("no_implicit_consumption", List.of(), List.of(check, item, optional), List.of());
        install(quest);
        engine.reconcile(player);
        player.getInventory().items.set(0, new ItemStack(Items.STONE, 2));
        player.getInventory().items.set(1, new ItemStack(Items.DIRT, 1));
        engine.completeTask(player, quest.id(), check.id());
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2, "sibling's items stay untouched");
        helper.assertValueEqual(engine.progress(player).taskProgress(item.id().toString()), 0L, "item awaits explicit submission");
        helper.assertTrue(engine.completeTask(player, quest.id(), item.id(), new TaskSubmissionSelection(List.of(0))).success(),
                "selected item row finishes the required objectives");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0, "only selected required items consumed");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 1, "optional items are not consumed at completion");
        helper.assertValueEqual(engine.progress(player).taskProgress(optional.id().toString()), 0L, "no invented optional receipt");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void newObjectivesPreserveHistoricalCompletionAndRewardClaims(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        TaskDefinition first = new TaskDefinition(id("book"), id("historical_first"), id("checkmark"), Map.of(), false);
        RewardDefinition reward = new RewardDefinition(id("book"), id("historical_reward"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:diamond\"}"), "auto", false);
        QuestDefinition original = quest("historical_quest", List.of(), List.of(first), List.of(reward));
        install(original);
        engine.reconcile(player);
        helper.assertTrue(engine.completeTask(player, original.id(), first.id()).success(), "original quest completes");
        long completedAt = engine.progress(player).completedAt(original.id().toString());

        // Install a new definition with the same stable quest ID, exactly as edit/reload reconciliation sees it.
        TaskDefinition added = consumingStoneTask("historical_added", 2);
        install(quest("historical_quest", List.of(), List.of(first, added), List.of(reward)));
        engine.reconcile(player);
        player.getInventory().items.set(1, new ItemStack(Items.STONE, 2));
        helper.assertValueEqual(engine.progress(player).status(original.id().toString()), QuestStatus.REWARD_CLAIMED,
                "author edits do not revoke historical completion");
        helper.assertValueEqual(engine.progress(player).taskProgress(first.id().toString()), 1L, "old receipt preserved");
        helper.assertValueEqual(engine.progress(player).taskProgress(added.id().toString()), 0L, "new objective gets no receipt");
        helper.assertValueEqual(engine.progress(player).completedAt(original.id().toString()), completedAt, "timestamp preserved");
        helper.assertTrue(!engine.completeTask(player, original.id(), added.id()).success(), "history does not accept extra submissions");
        helper.assertTrue(!engine.claim(player, reward.id()).changed(), "historical reward cannot be granted twice");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 1, "one reward only");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 2, "new requirement does not consume retroactively");
        var restored = yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress.load(engine.progress(player).save());
        helper.assertTrue(restored.isClaimed(reward.id().toString()), "reward claim survives ledger serialization");
        helper.assertValueEqual(restored.taskProgress(added.id().toString()), 0L, "serialization does not invent receipts");
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

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void remoteAdministratorsUseTargetServerPermissionsAndLeases(GameTestHelper helper) {
        var firstAdmin = helper.makeMockServerPlayerInLevel();
        var secondAdmin = helper.makeMockServerPlayerInLevel();
        var ordinaryPlayer = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        // Operator state belongs to the target server. A connecting client's local
        // configuration is never accepted as proof of authoring authority.
        // GameTestServer reports operator-user-permission-level 0, unlike a normal
        // dedicated server. Seed explicit level-2 OP entries to model its real policy.
        server.getPlayerList().getOps().add(new ServerOpListEntry(firstAdmin.getGameProfile(), 2, false));
        server.getPlayerList().getOps().add(new ServerOpListEntry(secondAdmin.getGameProfile(), 2, false));
        server.getPlayerList().sendPlayerPermissionLevel(firstAdmin);
        server.getPlayerList().sendPlayerPermissionLevel(secondAdmin);
        server.getPlayerList().deop(ordinaryPlayer.getGameProfile());
        ResourceLocation bookId = id("remote_author_" + firstAdmin.getUUID().toString().replace("-", ""));
        DraftService drafts = new DraftService();
        var deniedDraft = drafts.createEmpty(ordinaryPlayer, bookId, "Remote authoring");
        var createdDraft = drafts.createEmpty(firstAdmin, bookId, "Remote authoring");
        helper.assertValueEqual(deniedDraft.status(), AuthorOperationResult.Status.FORBIDDEN,
                "ordinary remote player must not create a server draft");
        helper.assertTrue(createdDraft.success(), "remote administrator must create a draft on the target server");
        var deniedCatalog = AuthorApi.catalog(ordinaryPlayer);
        var administratorCatalog = AuthorApi.catalog(firstAdmin);
        helper.assertValueEqual(deniedCatalog.status(), AuthorOperationResult.Status.FORBIDDEN,
                "ordinary remote player must not receive the server draft catalog");
        helper.assertTrue(administratorCatalog.success()
                        && administratorCatalog.value().stream().anyMatch(entry -> entry.bookId().equals(bookId)),
                "remote administrator must receive the target server draft catalog");
        DraftSnapshot draft = createdDraft.value();
        EditSessionService sessions = EditSessionService.get();

        var opened = sessions.open(firstAdmin, draft);
        var denied = sessions.open(ordinaryPlayer, draft);
        var occupied = sessions.open(secondAdmin, draft);

        helper.assertTrue(opened.success(), "connected target-server administrator must open an edit session: "
                + opened.status() + "/" + opened.code());
        helper.assertValueEqual(denied.status(), AuthorOperationResult.Status.FORBIDDEN,
                "ordinary remote player must not open an edit session");
        helper.assertValueEqual(occupied.status(), AuthorOperationResult.Status.CONFLICT,
                "a second remote administrator must observe the server-side writer lease");

        DraftEditService edits = new DraftEditService();
        var edited = edits.setBookTitle(firstAdmin, opened.value().sessionId(), bookId,
                draft.draftRevision(), "Edited on target server");
        var staleEdit = edits.setBookTitle(firstAdmin, opened.value().sessionId(), bookId,
                draft.draftRevision(), "Stale overwrite");
        helper.assertTrue(edited.success(), "authorized remote edit must update the server session draft");
        helper.assertValueEqual(staleEdit.status(), AuthorOperationResult.Status.CONFLICT,
                "a delayed remote request must not overwrite the newer draft revision");
        String editedRevision = edited.value().snapshot().draftRevision();
        var invalidEdit = edits.setBookTitle(firstAdmin, opened.value().sessionId(), bookId,
                editedRevision, "");
        helper.assertValueEqual(invalidEdit.status(), AuthorOperationResult.Status.INVALID_REQUEST,
                "invalid remote edit must be rejected before session state changes");
        helper.assertValueEqual(sessions.inspect(firstAdmin, bookId).value().draftRevision(), editedRevision,
                "rejected edit must preserve the previous server draft revision");
        helper.assertTrue(sessions.inspect(firstAdmin, bookId).value().dirty(),
                "an edited remote session must report unsaved changes");
        DraftDiffService diffs = new DraftDiffService();
        var dirtyDiff = diffs.preview(firstAdmin, opened.value().sessionId(), bookId, editedRevision,
                DraftDiffService.Baseline.SAVED_DRAFT);
        helper.assertTrue(dirtyDiff.success() && !dirtyDiff.value().empty(),
                "remote administrator must preview the session-to-disk semantic diff");
        DraftPersistenceService persistence = new DraftPersistenceService();
        var saved = persistence.save(firstAdmin, opened.value().sessionId(), bookId, editedRevision);
        var repeatedSave = persistence.save(firstAdmin, opened.value().sessionId(), bookId, editedRevision);
        helper.assertTrue(saved.success(), "validated remote session draft must save on the target server");
        helper.assertTrue(!sessions.inspect(firstAdmin, bookId).value().dirty(),
                "a successful target-server save must advance the session's saved revision");
        helper.assertValueEqual(repeatedSave.status(), AuthorOperationResult.Status.NO_CHANGE,
                "repeated save of identical semantic content must be idempotent");
        helper.assertValueEqual(diffs.preview(firstAdmin, opened.value().sessionId(), bookId, editedRevision,
                DraftDiffService.Baseline.SAVED_DRAFT).status(), AuthorOperationResult.Status.NO_CHANGE,
                "saved draft and session must have no semantic diff");
        String activeBeforePublish = QuestBookManager.get().active().orElseThrow().revision();
        DraftPublishService publisher = new DraftPublishService();
        var publishPreview = publisher.preview(firstAdmin, opened.value().sessionId(), bookId, editedRevision);
        helper.assertTrue(publishPreview.success()
                        && publishPreview.value().snapshot().draftRevision().equals(editedRevision)
                        && publishPreview.value().backup() == null,
                "publish preview must run the real gates without creating a workspace backup");
        helper.assertValueEqual(QuestBookManager.get().active().orElseThrow().revision(), activeBeforePublish,
                "publish preview must not change the active task-book snapshot");
        var published = publisher.publish(firstAdmin, opened.value().sessionId(), bookId, editedRevision);
        var repeatedPublish = publisher.publish(firstAdmin, opened.value().sessionId(), bookId, editedRevision);
        helper.assertTrue(published.success(), "saved remote draft must publish to the target server workspace");
        helper.assertValueEqual(repeatedPublish.status(), AuthorOperationResult.Status.NO_CHANGE,
                "repeated publish of identical content must be idempotent");
        helper.assertValueEqual(QuestBookManager.get().active().orElseThrow().revision(), activeBeforePublish,
                "publish must not change the active task-book snapshot before explicit deploy and reload");

        var editedAgain = edits.setBookTitle(firstAdmin, opened.value().sessionId(), bookId,
                editedRevision, "Edited again after publish");
        String secondRevision = editedAgain.value().snapshot().draftRevision();
        helper.assertValueEqual(publisher.publish(firstAdmin, opened.value().sessionId(), bookId,
                        secondRevision).code(), "UNSAVED_DRAFT",
                "publish must reject unsaved in-memory changes");
        helper.assertTrue(persistence.save(firstAdmin, opened.value().sessionId(), bookId, secondRevision).success(),
                "continued editing after publish must save against the rebased workspace revision");
        var secondPublish = publisher.publish(firstAdmin, opened.value().sessionId(), bookId, secondRevision);
        helper.assertTrue(secondPublish.success(),
                "continued editing must publish again without a false external-workspace conflict");

        AuthorBackupService backups = new AuthorBackupService();
        String workspaceBackupId = secondPublish.value().backup().getFileName().toString();
        helper.assertTrue(backups.list(firstAdmin, BackupKind.WORKSPACE).value().stream()
                        .anyMatch(backup -> backup.id().equals(workspaceBackupId)),
                "remote administrator must list target-server workspace backups by relative ID");
        helper.assertValueEqual(backups.preview(firstAdmin, BackupKind.WORKSPACE, "../workspace").code(),
                "BACKUP_NOT_FOUND", "backup preview must reject path traversal");
        var restorePreview = backups.preview(firstAdmin, BackupKind.WORKSPACE, workspaceBackupId);
        helper.assertTrue(restorePreview.success() && restorePreview.value().willReplace(),
                "remote administrator must preview a workspace restore before mutation");
        var restored = backups.restore(firstAdmin, BackupKind.WORKSPACE, workspaceBackupId,
                restorePreview.value().currentRevision());
        helper.assertTrue(restored.success() && restored.value().overwrittenBackup() != null,
                "workspace restore must preserve the overwritten target and complete atomically");
        helper.assertValueEqual(QuestBookManager.get().active().orElseThrow().revision(), activeBeforePublish,
                "workspace restore must not implicitly reload the active task-book snapshot");

        String importedBookPath = "import_" + firstAdmin.getUUID().toString().replace("-", "");
        try {
            var imported = new FtbImportService().execute(server, "eow", "brnquest", importedBookPath, false);
            ResourceLocation importedBookId = ResourceLocation.fromNamespaceAndPath("brnquest", importedBookPath);
            helper.assertTrue(imported.draftResult() != null && imported.draftResult().success(),
                    "FTB import must create an isolated server draft");
            helper.assertValueEqual(new DraftRepository().load(server, importedBookId).value().origin(),
                    DraftOrigin.IMPORT, "imported draft must preserve IMPORT provenance");
            helper.assertTrue(java.nio.file.Files.notExists(WorkspacePaths.workspace(server)
                            .resolve("data/brnquest/brnquest/books/" + importedBookPath + ".json"))
                            && java.nio.file.Files.notExists(server.getWorldPath(
                                    net.minecraft.world.level.storage.LevelResource.ROOT)
                                    .resolve("datapacks/brnquest-import-brnquest-" + importedBookPath)),
                    "FTB import must not write workspace or a directly loaded world data pack");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("FTB draft import GameTest failed", exception);
        }

        var authorCommand = server.getCommands().getDispatcher().getRoot().getChild("brnquest").getChild("author");
        helper.assertTrue(authorCommand != null && authorCommand.getChild("create") != null
                        && authorCommand.getChild("open") != null && authorCommand.getChild("validate") != null
                        && authorCommand.getChild("diff") != null && authorCommand.getChild("save") != null
                        && authorCommand.getChild("add_group") != null
                        && authorCommand.getChild("add_chapter") != null
                        && authorCommand.getChild("add_quest") != null
                        && authorCommand.getChild("add_dependency") != null
                        && authorCommand.getChild("add_task") != null
                        && authorCommand.getChild("add_reward") != null
                        && authorCommand.getChild("publish") != null && authorCommand.getChild("deploy") != null
                        && authorCommand.getChild("backups") != null
                        && authorCommand.getChild("restore_preview") != null
                        && authorCommand.getChild("restore") != null
                        && authorCommand.getChild("reload") != null,
                "dedicated-server dispatcher must expose the complete permission-gated author workflow");
        helper.assertTrue(authorCommand.canUse(firstAdmin.createCommandSourceStack()),
                "target-server administrator must satisfy the author command permission predicate");
        helper.assertTrue(!authorCommand.canUse(ordinaryPlayer.createCommandSourceStack()),
                "ordinary remote player must not discover or execute the author command tree");

        // Logout handling uses this same release path, allowing another administrator
        // to continue without waiting for the idle timeout.
        sessions.releasePlayer(server, firstAdmin.getUUID());
        helper.assertTrue(sessions.open(secondAdmin, draft).success(),
                "disconnect release must make the server-side draft available");
        sessions.releasePlayer(server, secondAdmin.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "stage4AuthorWorkflow")
    @PrefixGameTestTemplate(false)
    public static void authorCommandsBuildDeployAndReloadCompleteBook(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        server.getPlayerList().getOps().add(new ServerOpListEntry(admin.getGameProfile(), 2, false));
        server.getPlayerList().sendPlayerPermissionLevel(admin);

        ResourceLocation book = ResourceLocation.fromNamespaceAndPath("aaa_stage4",
                "core_acceptance_" + admin.getUUID().toString().replace("-", ""));
        ResourceLocation firstGroup = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "first_group");
        ResourceLocation secondGroup = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "second_group");
        ResourceLocation firstChapter = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "first_chapter");
        ResourceLocation secondChapter = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "second_chapter");
        ResourceLocation firstQuest = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "first_quest");
        ResourceLocation secondQuest = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "second_quest");
        ResourceLocation task = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "checkmark_task");
        ResourceLocation reward = ResourceLocation.fromNamespaceAndPath("aaa_stage4", "custom_reward");

        try {
            // Old acceptance runs may share the persistent GameTest workspace. Remove only
            // this test namespace so the freshly authored book is the lexically selected one.
            deleteTestTree(WorkspacePaths.workspace(server).resolve("data/aaa_stage4"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not isolate stage-4 command fixture", exception);
        }

        var created = new DraftService().createEmpty(admin, book, "Stage 4 command acceptance");
        var opened = EditSessionService.get().open(admin, created.value());
        helper.assertTrue(created.success() && opened.success(),
                "administrator must bootstrap the command-authored acceptance draft: create=" + created.code() + " " + created.message()
                        + "; open=" + opened.code() + " " + opened.message());
        String session = opened.value().sessionId().toString();
        String initialRevision = opened.value().session().draftRevision();

        helper.assertValueEqual(authorCommand(server, admin, "add_group " + session + " " + book + " "
                + currentRevision(admin, book) + " " + firstGroup + " 0 First group"), 1,
                "author command must add the first chapter group");
        helper.assertValueEqual(authorCommand(server, admin, "add_group " + session + " " + book + " "
                + currentRevision(admin, book) + " " + secondGroup + " 1 Second group"), 1,
                "author command must add the second chapter group");
        helper.assertValueEqual(authorCommand(server, admin, "add_chapter " + session + " " + book + " "
                + currentRevision(admin, book) + " " + firstChapter + " " + firstGroup
                + " 0 minecraft:stone First chapter"), 1,
                "author command must add a chapter using stable IDs");
        helper.assertValueEqual(authorCommand(server, admin, "add_chapter " + session + " " + book + " "
                + currentRevision(admin, book) + " " + secondChapter + " " + secondGroup
                + " 1 minecraft:diamond Second chapter"), 1,
                "author command must add a chapter to another group");
        helper.assertValueEqual(authorCommand(server, admin, "add_quest " + session + " " + book + " "
                + currentRevision(admin, book) + " " + firstQuest + " " + firstChapter
                + " -2.5 0 minecraft:stone First quest"), 1,
                "author command must add a positioned quest node");
        helper.assertValueEqual(authorCommand(server, admin, "add_quest " + session + " " + book + " "
                + currentRevision(admin, book) + " " + secondQuest + " " + secondChapter
                + " 2.5 0 minecraft:diamond Second quest"), 1,
                "author command must add a second positioned quest node");
        helper.assertValueEqual(authorCommand(server, admin, "add_dependency " + session + " " + book + " "
                + currentRevision(admin, book) + " " + secondQuest + " " + firstQuest), 1,
                "author command must add a cross-chapter dependency");
        helper.assertValueEqual(authorCommand(server, admin, "add_task " + session + " " + book + " "
                + currentRevision(admin, book) + " " + firstQuest + " " + task
                + " brnquest:checkmark false {\"title\":\"Confirm first quest\"}"), 1,
                "author command must add a typed task with JSON string-map config");
        helper.assertValueEqual(authorCommand(server, admin, "add_reward " + session + " " + book + " "
                + currentRevision(admin, book) + " " + firstQuest + " " + reward
                + " brnquest:custom manual false {}"), 1,
                "author command must add a typed reward");

        String completeRevision = currentRevision(admin, book);
        helper.assertValueEqual(authorCommand(server, admin, "set_title " + session + " " + book + " "
                + initialRevision + " Stale overwrite"), 0,
                "stale command revision must not overwrite the complete draft");
        helper.assertValueEqual(currentRevision(admin, book), completeRevision,
                "rejected stale command must preserve the server session snapshot");
        helper.assertTrue(authorCommandFails(server, admin, "add_task " + session + " " + book + " "
                        + completeRevision + " " + firstQuest + " aaa_stage4:invalid brnquest:custom false {\"nested\":{}}"),
                "nested command config must be rejected instead of being flattened ambiguously");
        helper.assertValueEqual(currentRevision(admin, book), completeRevision,
                "invalid command config must not mutate the draft");

        helper.assertValueEqual(authorCommand(server, admin, "validate " + session + " " + book + " "
                + completeRevision), 1, "complete command-authored draft must validate");
        helper.assertValueEqual(authorCommand(server, admin, "diff " + session + " " + book + " "
                + completeRevision + " workspace"), 1, "command must preview the unpublished workspace diff");
        helper.assertValueEqual(authorCommand(server, admin, "save " + session + " " + book + " "
                + completeRevision), 1, "command must save the complete draft");
        helper.assertValueEqual(authorCommand(server, admin, "publish " + session + " " + book + " "
                + completeRevision), 1, "command must publish the saved draft to workspace");
        helper.assertTrue(!QuestBookManager.get().active().orElseThrow().book().id().equals(book),
                "publish must not change the active task book");
        helper.assertValueEqual(authorCommand(server, admin, "deploy --replace"), 1,
                "command must deploy the author workspace with backup replacement");
        helper.assertTrue(!QuestBookManager.get().active().orElseThrow().book().id().equals(book),
                "deploy must still wait for an explicit reload");
        helper.assertValueEqual(authorCommand(server, admin, "reload"), 1,
                "command must request the final resource reload");

        helper.succeedWhen(() -> {
            var active = QuestBookManager.get().active().orElseThrow();
            helper.assertValueEqual(active.book().id(), book,
                    "reload must atomically activate the command-authored task book");
            helper.assertValueEqual(active.book().chapterGroups().size(), 2,
                    "active book must retain both authored chapter groups");
            helper.assertValueEqual(active.book().chapters().size(), 2,
                    "active book must retain both authored chapters");
            helper.assertValueEqual(active.book().quests().size(), 2,
                    "active book must retain both authored quests");
            helper.assertTrue(active.quests().get(secondQuest).dependencies().contains(firstQuest)
                            && active.quests().get(firstQuest).tasks().stream().anyMatch(value -> value.id().equals(task))
                            && active.quests().get(firstQuest).rewards().stream().anyMatch(value -> value.id().equals(reward)),
                    "reload must retain dependency, task, and reward content");
        });
    }

    private static int authorCommand(net.minecraft.server.MinecraftServer server,
                                     net.minecraft.server.level.ServerPlayer player, String suffix) {
        try {
            return server.getCommands().getDispatcher().execute("brnquest author " + suffix,
                    player.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException("Author command failed to parse: " + suffix, exception);
        }
    }

    private static boolean authorCommandFails(net.minecraft.server.MinecraftServer server,
                                              net.minecraft.server.level.ServerPlayer player, String suffix) {
        try {
            server.getCommands().getDispatcher().execute("brnquest author " + suffix,
                    player.createCommandSourceStack());
            return false;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            return true;
        }
    }

    private static String currentRevision(net.minecraft.server.level.ServerPlayer player, ResourceLocation book) {
        return EditSessionService.get().inspect(player, book).value().draftRevision();
    }

    private static void deleteTestTree(java.nio.file.Path root) throws java.io.IOException {
        if (!java.nio.file.Files.exists(root)) return;
        try (var paths = java.nio.file.Files.walk(root)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    private static QuestDefinition quest(String path, List<ResourceLocation> dependencies,
                                         List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id(path), id("chapter"), path, "", "", "", 0, 0,
                dependencies, tasks, rewards, path.toUpperCase(java.util.Locale.ROOT));
    }

    private static QuestDefinition quest(String path, List<ResourceLocation> dependencies,
                                         List<TaskDefinition> tasks, List<RewardDefinition> rewards,
                                         QuestBehavior behavior) {
        return new QuestDefinition(id("book"), id(path), id("chapter"), path, "", "", "", 0, 0,
                dependencies, tasks, rewards, path.toUpperCase(java.util.Locale.ROOT),
                QuestAppearance.DEFAULT, behavior, Map.of());
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

package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressService;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server integration tests use real progress/reward services and isolated, temporarily online mock players. */
@GameTestHolder("brnquest")
public final class AdminProgressGameTests {
    private AdminProgressGameTests() {}

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void adminTaskResetPreservesClaimsAndReplayDoesNotRepeatEffects(GameTestHelper helper) {
        try (OnlinePlayers online = new OnlinePlayers(helper)) {
            var engine = ProgressEngine.get();
            var service = AdminProgressService.get();
            var admin = online.admin;
            var target = online.target;
            var quest = quest("admin_flow");
            install(quest);
            engine.reconcile(target);
            engine.reconcile(admin);
            target.getInventory().add(new ItemStack(Items.STONE, 4));

            var first = preview(online, quest, "admin_item", AdminProgressAction.FORCE_TASK);
            helper.assertTrue(first.result().success() && !first.view().completesQuest(), "first task must not finish the quest");
            helper.assertTrue(service.confirm(admin.createCommandSourceStack(), first.token()).result().changed(), "first task commits");
            helper.assertValueEqual(target.getInventory().countItem(Items.STONE), 4, "admin completion never consumes items");
            helper.assertValueEqual(target.getInventory().countItem(Items.DIAMOND), 0, "remaining required objective blocks rewards");
            helper.assertValueEqual(engine.progress(admin).taskProgress(id("admin_item").toString()), 0L,
                    "targeted management must not change the actor's ledger");

            var last = preview(online, quest, "admin_check", AdminProgressAction.FORCE_TASK);
            helper.assertTrue(last.view().completesQuest() && last.view().automaticRewards() == 1, "preview must disclose automatic rewards");
            helper.assertTrue(service.confirm(admin.createCommandSourceStack(), last.token()).result().success(), "last task commits");
            helper.assertValueEqual(target.getInventory().countItem(Items.DIAMOND), 1, "automatic reward is delivered once");
            helper.assertValueEqual(engine.progress(target).status(quest.id().toString()), QuestStatus.REWARD_CLAIMED, "claims are terminal");

            var reset = preview(online, quest, "admin_item", AdminProgressAction.RESET_TASK);
            helper.assertTrue(!reset.view().clearsRewardClaims(), "task reset must explicitly preserve reward claims");
            service.confirm(admin.createCommandSourceStack(), reset.token());
            helper.assertValueEqual(engine.progress(target).taskProgress(id("admin_item").toString()), 0L, "selected task is cleared");
            helper.assertTrue(engine.progress(target).taskProgress(id("admin_check").toString()) >= 1, "other task is preserved");
            helper.assertTrue(engine.progress(target).isClaimed(id("admin_reward").toString()), "reward claim survives reopening");
            // Replaying an old completion after a reset must not force-complete the new live state.
            service.confirm(admin.createCommandSourceStack(), last.token());
            helper.assertValueEqual(engine.progress(target).status(quest.id().toString()), QuestStatus.AVAILABLE, "replay cannot re-complete reopened quest");
            var recomplete = preview(online, quest, "admin_item", AdminProgressAction.FORCE_TASK);
            service.confirm(admin.createCommandSourceStack(), recomplete.token());
            helper.assertValueEqual(target.getInventory().countItem(Items.DIAMOND), 1, "re-completing a task never duplicates claimed reward");
            helper.assertValueEqual(engine.progress(target).status(quest.id().toString()), QuestStatus.REWARD_CLAIMED, "preserved claims restore terminal state");

            var allReset = preview(online, quest, "", AdminProgressAction.RESET_QUEST);
            helper.assertTrue(allReset.view().clearsRewardClaims(), "whole-quest reset discloses cleared claims");
            service.confirm(admin.createCommandSourceStack(), allReset.token());
            helper.assertTrue(!engine.progress(target).isClaimed(id("admin_reward").toString()), "whole reset clears claims");
            helper.assertValueEqual(target.getInventory().countItem(Items.DIAMOND), 1, "reset does not reclaim delivered items");
            // Execute the registered command, not just the facade, to verify its shared semantics.
            String selectorTag = "o12_target_" + target.getUUID().toString().replace("-", "");
            target.addTag(selectorTag);
            int result = admin.getServer().getCommands().getDispatcher().execute(
                    "brnquest progress complete @a[tag=" + selectorTag + ",limit=1] \"" + quest.id() + "\"", admin.createCommandSourceStack());
            helper.assertValueEqual(result, 1, "existing command routes through the administrator service");
            helper.assertValueEqual(target.getInventory().countItem(Items.DIAMOND), 2, "whole reset permits one fresh automatic reward");
            service.confirm(admin.createCommandSourceStack(), allReset.token());
            helper.assertTrue(engine.progress(target).isClaimed(id("admin_reward").toString()), "reset replay cannot erase a newer reward claim");
            helper.assertValueEqual(target.getInventory().countItem(Items.DIAMOND), 2, "replay leaves inventory intact");
            helper.succeed();
        } catch (Exception exception) { throw new IllegalStateException("Admin progress integration failed", exception); }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void adminConfirmationRevalidatesAuthorityPublicationAndProgress(GameTestHelper helper) {
        try (OnlinePlayers online = new OnlinePlayers(helper)) {
            var service = AdminProgressService.get();
            var engine = ProgressEngine.get();
            var actor = online.admin.createCommandSourceStack();
            var quest = quest("admin_guards");
            install(quest);
            engine.reconcile(online.target);
            helper.assertValueEqual(service.catalog(online.target.createCommandSourceStack().withPermission(0), "").result().code(),
                    "FORBIDDEN", "ordinary players cannot list progress-management targets");
            helper.assertTrue(service.catalog(actor, online.target.getScoreboardName()).players().stream()
                    .anyMatch(player -> player.id().equals(online.target.getUUID().toString())), "catalog filters real online identities");
            var valid = intent(online, quest, "", AdminProgressAction.FORCE_QUEST);
            var unpublished = new AdminProgressService.Intent(valid.targetId(), valid.bookId(), valid.revision(),
                    "brnquest:unpublished_draft_only", "", valid.action());
            helper.assertValueEqual(service.inspect(actor, unpublished, true).result().code(), "NOT_PUBLISHED", "draft-only IDs cannot mutate live progress");
            helper.assertValueEqual(preview(online, quest, "wrong_task", AdminProgressAction.FORCE_TASK).result().code(),
                    "NOT_PUBLISHED", "a task must belong to the selected quest");
            var offline = new AdminProgressService.Intent(UUID.randomUUID().toString(), valid.bookId(), valid.revision(),
                    valid.questId(), "", valid.action());
            helper.assertValueEqual(service.inspect(actor, offline, true).result().code(), "PLAYER_OFFLINE", "offline targets fail closed");

            var ticket = service.inspect(actor, valid, true);
            helper.assertValueEqual(service.confirm(online.target.createCommandSourceStack().withPermission(2), ticket.token()).result().code(),
                    "CONFIRMATION_EXPIRED", "another administrator cannot use someone else's confirmation ticket");
            helper.assertValueEqual(service.confirm(actor.withPermission(0), ticket.token()).result().code(), "FORBIDDEN", "permission is checked again on commit");
            helper.assertValueEqual(service.confirm(actor, ticket.token()).result().code(), "CONFIRMATION_EXPIRED", "revoked preview cannot later be replayed");
            var changed = preview(online, quest, "", AdminProgressAction.RESET_QUEST);
            engine.addTaskProgress(online.target, id("admin_check"), 1);
            helper.assertValueEqual(service.confirm(actor, changed.token()).result().code(), "PROGRESS_CHANGED", "concurrent progress invalidates destructive preview");
            helper.assertTrue(engine.progress(online.target).taskProgress(id("admin_check").toString()) >= 1, "rejected reset preserves new progress");

            var revisionTicket = preview(online, quest, "", AdminProgressAction.FORCE_QUEST);
            install(new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(), "New published title", "", "", "",
                    0, 0, quest.dependencies(), quest.tasks(), quest.rewards(), ""));
            helper.assertValueEqual(service.confirm(actor, revisionTicket.token()).result().code(), "STALE_REVISION", "publication invalidates preview");
            var disconnectTicket = preview(online, quest, "", AdminProgressAction.FORCE_QUEST);
            online.target.getServer().getPlayerList().remove(online.target);
            helper.assertValueEqual(service.confirm(actor, disconnectTicket.token()).result().code(), "PLAYER_OFFLINE", "disconnect invalidates confirmation");
            helper.assertValueEqual(online.target.getInventory().countItem(Items.DIAMOND), 0, "all rejected attempts grant no rewards");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void adminTaskCompletionRespectsDependenciesAndResetDoesNotCascade(GameTestHelper helper) {
        try (OnlinePlayers online = new OnlinePlayers(helper)) {
            var service = AdminProgressService.get();
            var engine = ProgressEngine.get();
            var base = quest("admin_dependencies");
            var parent = new QuestDefinition(id("book"), id("admin_parent"), id("chapter"), "Parent", "", "", "",
                    0, 0, List.of(), List.of(new TaskDefinition(id("book"), id("parent_check"), id("checkmark"), Map.of(), false)), List.of(), "");
            var gated = new QuestDefinition(base.bookId(), base.id(), base.chapterId(), base.title(), "", "", "",
                    1, 0, List.of(parent.id()), base.tasks(), base.rewards(), "");
            var downstream = new QuestDefinition(id("book"), id("admin_downstream"), id("chapter"), "Downstream", "", "", "",
                    2, 0, List.of(gated.id()), List.of(), List.of(), "");
            install(parent, gated, downstream);
            engine.reconcile(online.target);
            var first = preview(online, gated, "admin_item", AdminProgressAction.FORCE_TASK);
            service.confirm(online.admin.createCommandSourceStack(), first.token());
            var last = preview(online, gated, "admin_check", AdminProgressAction.FORCE_TASK);
            helper.assertTrue(!last.view().completesQuest(), "a task-level override does not bypass quest dependencies");
            service.confirm(online.admin.createCommandSourceStack(), last.token());
            helper.assertValueEqual(engine.progress(online.target).status(gated.id().toString()), QuestStatus.LOCKED,
                    "completed task ledgers alone do not unlock a dependency-gated quest");
            helper.assertValueEqual(online.target.getInventory().countItem(Items.DIAMOND), 0, "locked quest grants no reward");
            var forceAll = preview(online, gated, "", AdminProgressAction.FORCE_QUEST);
            helper.assertTrue(forceAll.view().completesQuest(), "explicit whole-quest override does bypass dependencies");
            service.confirm(online.admin.createCommandSourceStack(), forceAll.token());
            helper.assertValueEqual(online.target.getInventory().countItem(Items.DIAMOND), 1, "whole override grants the auto reward");
            engine.forceComplete(online.target, downstream.id());
            service.command(online.admin.createCommandSourceStack(), online.target, gated.id().toString(), AdminProgressAction.RESET_QUEST);
            helper.assertValueEqual(engine.progress(online.target).status(gated.id().toString()), QuestStatus.LOCKED, "reset rechecks dependencies");
            helper.assertValueEqual(engine.progress(online.target).status(downstream.id().toString()), QuestStatus.COMPLETED,
                    "upstream reset does not cascade into already completed downstream quests");
            var noChange = preview(online, gated, "", AdminProgressAction.RESET_QUEST);
            helper.assertTrue(!service.confirm(online.admin.createCommandSourceStack(), noChange.token()).result().changed(),
                    "resetting an already reset quest is an explicit no-op");
            helper.succeed();
        }
    }

    private static AdminProgressService.Reply preview(OnlinePlayers online, QuestDefinition quest, String task,
                                                     AdminProgressAction action) {
        return AdminProgressService.get().inspect(online.admin.createCommandSourceStack(), intent(online, quest, task, action), true);
    }

    private static AdminProgressService.Intent intent(OnlinePlayers online, QuestDefinition quest, String task,
                                                      AdminProgressAction action) {
        var snapshot = QuestBookManager.get().active().orElseThrow();
        return new AdminProgressService.Intent(online.target.getUUID().toString(), snapshot.book().id().toString(),
                snapshot.revision(), quest.id().toString(), task.isEmpty() ? "" : id(task).toString(), action);
    }

    private static QuestDefinition quest(String name) {
        var item = new TaskDefinition(id("book"), id("admin_item"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:stone\"}", "count", "3", "consume_items", "1b"), false);
        var check = new TaskDefinition(id("book"), id("admin_check"), id("checkmark"), Map.of(), false);
        var reward = new RewardDefinition(id("book"), id("admin_reward"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:diamond\"}"), "auto", false);
        return new QuestDefinition(id("book"), id(name), id("chapter"), "管理验收", "", "", "", 0, 0,
                List.of(), List.of(item, check), List.of(reward), "");
    }

    private static void install(QuestDefinition... quests) {
        var book = new QuestBookDefinition(id("book"), 1, "Admin progress acceptance",
                List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, List.of(quests))), Map.of());
        if (!QuestBookManager.get().install(book, new DiagnosticReport())) throw new IllegalStateException("Invalid admin test book");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("brnquest", path); }

    /** Vanilla's embedded mock login registers real online indices; remove only our two players afterward. */
    private static final class OnlinePlayers implements AutoCloseable {
        final ServerPlayer admin, target;
        private final PlayerList playerList;

        @SuppressWarnings("removal") // Existing 1.21.1 fixture uses embedded login, which exercises online validation.
        OnlinePlayers(GameTestHelper helper) {
            admin = helper.makeMockServerPlayerInLevel();
            target = helper.makeMockServerPlayerInLevel();
            playerList = helper.getLevel().getServer().getPlayerList();
            playerList.getOps().add(new ServerOpListEntry(admin.getGameProfile(), 2, false));
            playerList.sendPlayerPermissionLevel(admin);
        }

        @Override public void close() {
            if (playerList.getPlayer(admin.getUUID()) == admin) playerList.remove(admin);
            if (playerList.getPlayer(target.getUUID()) == target) playerList.remove(target);
            playerList.getOps().remove(admin.getGameProfile());
        }
    }
}

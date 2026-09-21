package yourscraft.jasdewstarfield.brnquest.command;

import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.author.DraftService;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionService;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import java.util.ArrayList;
import java.util.List;

/** Runs real command permission and read-only checks with an actual author lease on the isolated server. */
@GameTestHolder(BRNQuest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HealthCommandGameTests {
    private HealthCommandGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "healthCommand")
    public static void healthCommandObservesWithoutChangingAnAuthorSession(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        var server = player.getServer();
        var players = server.getPlayerList();
        players.getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        players.sendPlayerPermissionLevel(player);
        try {
            var bookId = ResourceLocation.parse("brnquest:health_" + player.getUUID().toString().replace("-", ""));
            var created = new DraftService().createEmpty(player, bookId, "Health observation");
            helper.assertTrue(created.success(), created.code());
            var opened = EditSessionService.get().open(player, created.value());
            helper.assertTrue(opened.success(), opened.code());
            var beforeLease = EditSessionService.get().inspect(player, bookId).value();
            helper.runAfterDelay(5, () -> {
                try {
                    var beforeHealth = QuestBookManager.get().health();
                    yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork.syncAll(player, false);
                    var beforeSend = yourscraft.jasdewstarfield.brnquest.network.BookSyncObservations.get().inspect(player.getUUID());
                    helper.assertValueEqual(beforeSend.status(), yourscraft.jasdewstarfield.brnquest.network.BookSyncObservations.Status.NOT_SUBMITTED,
                            "mock connection without a negotiated channel cannot claim submitted chunks");
                    List<Component> messages = new ArrayList<>();
                    CommandSource sink = new CommandSource() {
                        public void sendSystemMessage(Component message) { messages.add(message); }
                        public boolean acceptsSuccess() { return true; }
                        public boolean acceptsFailure() { return true; }
                        public boolean shouldInformAdmins() { return false; }
                    };
                    var source = player.createCommandSourceStack().withSource(sink).withPermission(2);
                    var dispatcher = server.getCommands().getDispatcher();
                    helper.assertTrue(!dispatcher.getRoot().getChild("brnquest").getChild("health")
                            .canUse(source.withPermission(0)), "ordinary players cannot inspect health");
                    helper.assertValueEqual(dispatcher.execute("brnquest health", source), 1, "summary executes");
                    helper.assertValueEqual(dispatcher.execute("brnquest health details", source), 1, "details executes");
                    helper.assertValueEqual(dispatcher.execute("brnquest health player " + player.getGameProfile().getName() + " details", source), 1,
                            "per-player observations execute through the actual command");
                    helper.assertValueEqual(yourscraft.jasdewstarfield.brnquest.network.BookSyncObservations.get().inspect(player.getUUID()), beforeSend,
                            "query does not send packets or change observations");
                    helper.assertTrue(!messages.isEmpty(), "command delivers observable output");
                    helper.assertValueEqual(QuestBookManager.get().health(), beforeHealth, "query preserves active pointer and reload observation");
                    helper.assertValueEqual(EditSessionService.get().inspect(player, bookId).value(), beforeLease,
                            "query preserves draft revision, saved revision, history and lease expiry");
                    // Historical task ledgers can outgrow the wire budget independently of the current book size.
                    var progress = yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine.get().progress(player);
                    for (int i = 0; i < 5500; i++) progress.addTaskProgress("health:historical_" + i + "x".repeat(200), 1);
                    var beforeTasks = progress.taskProgressView();
                    yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork.syncProgress(player, true);
                    var failure = yourscraft.jasdewstarfield.brnquest.network.ProgressSyncFailures.get().inspect(player.getUUID()).orElseThrow();
                    helper.assertValueEqual(failure.code(), "PROGRESS_TOO_LARGE", "oversize reports an explicit failure");
                    helper.assertTrue(failure.bytes() > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_PROGRESS_BYTES,
                            "failure reports actual UTF-8 size");
                    helper.assertValueEqual(progress.taskProgressView(), beforeTasks, "failed sync preserves server progress");
                    messages.clear();
                    dispatcher.execute("brnquest health player " + player.getGameProfile().getName() + " details", source);
                    helper.assertTrue(messages.stream().anyMatch(message -> message.getContents() instanceof
                            net.minecraft.network.chat.contents.TranslatableContents text
                            && text.getKey().equals("command.brnquest.health.player.progress_failed")),
                            "administrator sees progress failure through the actual health command");
                    helper.assertValueEqual(yourscraft.jasdewstarfield.brnquest.network.ProgressSyncFailures.get().inspect(player.getUUID()).orElseThrow(),
                            failure, "diagnostic query does not mutate failure state");
                    // A failed load keeps the previous active object; observing it must also retain that object.
                    helper.assertTrue(!QuestBookManager.get().install(null, new DiagnosticReport()), "invalid candidate rejected");
                    var rejected = QuestBookManager.get().health();
                    messages.clear();
                    dispatcher.execute("brnquest health details", source);
                    helper.assertValueEqual(QuestBookManager.get().health(), rejected, "failure inspection is read-only");
                    helper.assertTrue(messages.stream().anyMatch(message -> message.getContents() instanceof
                            net.minecraft.network.chat.contents.TranslatableContents text
                            && text.getKey().equals("command.brnquest.health.reload.rejected")), "failed reload has a localized summary");
                    helper.succeed();
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                } finally {
                    EditSessionService.get().releasePlayer(server, player.getUUID());
                    players.getOps().remove(player.getGameProfile());
                    if (players.getPlayer(player.getUUID()) == player) players.remove(player);
                }
            });
        } catch (Exception failure) {
            EditSessionService.get().releasePlayer(server, player.getUUID());
            players.getOps().remove(player.getGameProfile());
            if (players.getPlayer(player.getUUID()) == player) players.remove(player);
            throw failure;
        }
    }
}

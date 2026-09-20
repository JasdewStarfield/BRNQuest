package yourscraft.jasdewstarfield.brnquest.moduletest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestProgressData;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Four real server processes share a world: seed, missing module, restore, receipt recovery. */
@EventBusSubscriber(modid = "brnquest_module_test")
public final class ModuleSmoke {
    private static final UUID PLAYER = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static boolean ran;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (ran || event.getServer().getTickCount() < 40) return;
        ran = true;
        String phase = System.getProperty("brnquest.module.phase");
        try {
            verify(event.getServer(), phase);
            BRNQuest.LOGGER.info("[BRNQuest/MODULE_SMOKE] {} PASS", phase);
        } catch (Throwable failure) {
            BRNQuest.LOGGER.error("[BRNQuest/MODULE_SMOKE] FAILED " + phase, failure);
        }
        // A normal shutdown flushes SavedData for the next fresh JVM; Gradle checks the PASS marker.
        event.getServer().halt(false);
    }

    private static void verify(MinecraftServer server, String phase) throws Exception {
        boolean absent = phase.equals("absent");
        check((TaskTypeRegistry.get(ResourceLocation.parse("brnquest:checkmark")) == null) == absent,
                "common provider follows installed module");
        check((RewardTypeRegistry.get(ResourceLocation.parse("brnquest:xp")) == null) == absent,
                "reward provider follows installed module");
        if (absent) {
            try {
                Class.forName("yourscraft.jasdewstarfield.brnquest.builtin.BuiltinPlugins");
                throw new AssertionError("built-in bytecode leaked into the core-only runtime");
            } catch (ClassNotFoundException expected) { /* Absence is physical, not an emptied registry. */ }
        }
        Path file = Path.of("config/brnquest/module-book.json");
        if (phase.equals("seed")) {
            Files.createDirectories(file.getParent());
            try (var stream = ModuleSmoke.class.getResourceAsStream("/module-book.json")) {
                if (stream == null) throw new AssertionError("missing old-book fixture");
                Files.write(file, stream.readAllBytes());
            }
        }
        var book = NativeBookJson.decode(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        String canonical = NativeBookJson.encode(book);
        // Exercise the same native write/read codec with opaque task and reward fields still present.
        Files.writeString(file, canonical, StandardCharsets.UTF_8);
        book = NativeBookJson.decode(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        check(NativeBookJson.encode(book).equals(canonical), "unknown config round trip");
        check(book.quests().getFirst().tasks().getFirst().config().get("nested").equals("{\"future\":true}"),
                "opaque nested payload survives all phases");
        check(QuestBookManager.get().install(book, new DiagnosticReport()), "book remains installable");
        var codes = QuestBookManager.get().lastReport().diagnostics().stream().map(d -> d.code()).toList();
        check(codes.contains("BQV-117") == absent && codes.contains("BQV-118") == absent,
                "missing task/reward diagnostics disappear only after restoration");
        var player = player(server);
        var engine = ProgressEngine.get();
        engine.reconcile(player);
        if (phase.equals("seed")) {
            engine.reset(player, id("pending"));
            engine.reset(player, id("completed"));
            check(engine.submitTask(player, id("completed"), id("completed_task"), TaskSubmissionSelection.AUTOMATIC).changed(),
                    "seed an actual completed but unclaimed quest");
        } else if (absent) {
            check(engine.progress(player).status(id("completed").toString()) == QuestStatus.COMPLETED,
                    "pre-existing completion persists without implementation");
            check(engine.submitTask(player, id("pending"), id("pending_task"), TaskSubmissionSelection.AUTOMATIC)
                    .code().equals("UNKNOWN_TYPE"), "missing type cannot submit");
            engine.tick(player);
            check(engine.progress(player).status(id("pending").toString()) != QuestStatus.COMPLETED,
                    "missing type cannot passively complete");
            check(engine.claim(player, id("completed_reward")).code().equals("UNKNOWN_TYPE"),
                    "eligible missing reward cannot claim");
            check(player.totalExperience == 0 && !engine.rewardClaimed(player, book.quests().get(1).rewards().getFirst()),
                    "no effect or receipt fabricated");
        } else if (phase.equals("restored")) {
            check(engine.submitTask(player, id("pending"), id("pending_task"), TaskSubmissionSelection.AUTOMATIC).changed(),
                    "restored task resumes");
            check(engine.claim(player, id("pending_reward")).changed(), "restored pending reward");
            check(engine.claim(player, id("completed_reward")).changed(), "pre-removal completion remains claimable");
            check(player.totalExperience == 14, "effects execute exactly once");
            // Reinstall and reconnect through fresh player state without replacing the persisted owner ledger.
            check(QuestBookManager.get().install(book, new DiagnosticReport()), "reload the same book");
            OwnerRuntime.logout(player);
            player = player(server);
            OwnerRuntime.login(player);
            check(!engine.claim(player, id("completed_reward")).changed(), "reconnect preserves receipt");
        } else if (phase.equals("restart")) {
            check(!engine.claim(player, id("pending_reward")).changed(), "saved pending receipt survives restart");
            check(!engine.claim(player, id("completed_reward")).changed(), "saved completed receipt survives restart");
            check(player.totalExperience == 0, "restart never replays side effects");
            engine.reset(player, id("pending"));
            check(engine.submitTask(player, id("pending"), id("pending_task"), TaskSubmissionSelection.AUTOMATIC).changed(),
                    "reset creates a new completable cycle");
            check(engine.claim(player, id("pending_reward")).changed() && player.totalExperience == 7,
                    "new cycle grants once after reset");
        } else throw new AssertionError("Unknown fixture phase " + phase);
        QuestProgressData.get(server).setDirty();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return new ServerPlayer(server, server.overworld(), new GameProfile(PLAYER, "ModuleFixture"), ClientInformation.createDefault());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("module_test", path); }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}

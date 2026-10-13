package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real native counters and owner-ledger persistence in the disposable automated world. */
@GameTestHolder("brnquest_statistics")
@SuppressWarnings("removal")
public final class StatisticsGameTests {
    @GameTest(template = "empty", batch = "statisticRegistries")
    @PrefixGameTestTemplate(false)
    public static void allNineRegistriesAndNativeUnitsResolve(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var type = TaskTypeRegistry.get(StatisticsConfig.ID);
        helper.assertTrue(type instanceof StatisticsTask, "real bootstrap registers the statistics type");
        for (String category : StatisticsConfig.TYPES) {
            String target = category.equals("minecraft:custom") ? "minecraft:jump"
                    : category.equals("minecraft:mined") ? "minecraft:stone"
                    : category.equals("minecraft:killed") || category.equals("minecraft:killed_by") ? "minecraft:zombie" : "minecraft:apple";
            var config = StatisticsConfig.parse(Map.of("stat_type", category, "stat", target));
            var stat = StatisticsTargets.resolve(config).orElseThrow();
            player.getStats().setValue(player, stat, 12);
            var definition = task("registry", Map.of("stat_type", category, "stat", target, "count", "20"));
            long sample = new StatisticsTask().sampledProgress(context(player, definition, 0), definition.config());
            helper.assertTrue(sample == 12, "reads actual server counter for " + category);
        }
        var formatted = Map.of("play_time", "1.00 m", "walk_one_cm", "12.00 m", "damage_dealt", "120.00", "jump", "1,200");
        for (String target : formatted.keySet()) {
            var config = StatisticsConfig.parse(Map.of("stat", "minecraft:" + target));
            helper.assertTrue(StatisticsTargets.format(config, 1200).equals(formatted.get(target)),
                    "native formatter survives for " + target);
        }
        // CUSTOM values are identity-keyed. Resolution must use the registry value, not a newly parsed ID object.
        helper.assertTrue(StatisticsTargets.resolve(StatisticsConfig.parse(Map.of("stat", "minecraft:jump"))).orElseThrow()
                == Stats.CUSTOM.get(Stats.JUMP), "canonical custom statistic instance retains its formatter");
        var missing = task("missing", Map.of("stat_type", "minecraft:mined", "stat", "example:removed_block"));
        var implementation = new StatisticsTask();
        helper.assertTrue(StatisticsTargets.resolve(StatisticsConfig.parse(missing.config())).isEmpty(), "missing ID never resolves to air");
        helper.assertTrue(!implementation.satisfied(context(player, missing, 100), missing.config()), "missing registry target cannot complete from old progress");
        helper.assertTrue(implementation.configCodec().parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.Gson().toJsonTree(missing.config())).result().isPresent(), "opaque missing target survives loading");
        helper.assertTrue(!ConfigEditorSchemas.validate(StatisticsConfig.fields(), missing.config()).isEmpty(), "editor revalidates missing target");
        boolean rejected = false;
        try { implementation.normalizeConfig(ConfigNormalizationContext.withRegistries(player.registryAccess()), missing.config()); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "author write rejects missing category/target pair");
        var first = StatisticsTargets.page("minecraft:used", "", "minecraft:apple", 0);
        var ids = new HashSet<String>();
        for (int offset = 0; offset < first.total(); offset += ServerFieldSources.PAGE_SIZE) {
            var page = StatisticsTargets.page("minecraft:used", "", "minecraft:apple", offset);
            helper.assertTrue(page.entries().size() <= 64, "bounded selector page");
            page.entries().forEach(entry -> ids.add(entry.value()));
        }
        helper.assertTrue(first.total() > 64 && ids.size() == first.total(), "all registry IDs reachable through paging");
        helper.assertTrue(StatisticsTargets.page("minecraft:custom", "JUMP", "minecraft:jump", 0).total() == 1, "case-insensitive search");
        helper.assertTrue(StatisticsTargets.page("minecraft:mined", "", "minecraft:jump", 0).selectedCount() == 0, "dependent category changes invalidate the old target");
        helper.assertTrue(ServerFieldSources.query(player, StatisticsTargets.SOURCE, "", "minecraft:jump").error().equals("permission"), "author selector keeps permission boundary");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "statisticHistory", timeoutTicks = 140)
    @PrefixGameTestTemplate(false)
    public static void historyLatchesPersistsAndResetReadsHistoryAgain(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        var goal = task("jump", Map.of("stat", "minecraft:jump", "count", "20"));
        var gate = new TaskDefinition(id("book"), id("gate"), TaskTypes.CHECKMARK, Map.of(), false);
        var quest = install("history", List.of(gate, goal), true);
        var engine = ProgressEngine.get();
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.JUMP), 12);
        other.getStats().setValue(other, Stats.CUSTOM.get(Stats.JUMP), 3);
        engine.reconcile(player); engine.reconcile(other);
        var phase = new AtomicInteger();
        helper.succeedWhen(() -> {
            engine.pollTasks(player);
            if (phase.get() == 0) {
                helper.assertTrue(player.server.getTickCount() % 3 == 0, "wait for a sampling tick");
                helper.assertTrue(engine.taskProgress(player, quest, goal) == 0, "sequential future objective is not sampled");
                engine.complete(player, quest.id(), true);
                engine.complete(other, quest.id(), true);
                phase.set(1);
                helper.assertTrue(false, "wait for newly eligible history");
            }
            if (phase.get() == 1) {
                helper.assertTrue(engine.taskProgress(player, quest, goal) == 12, "historical actions before activation count");
                engine.pollTasks(other);
                helper.assertTrue(engine.taskProgress(other, quest, goal) == 3, "personal histories remain separate");
                var saved = QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
                player.server.overworld().getDataStorage().set("brnquest_progress", QuestProgressData.load(saved, player.registryAccess()));
                helper.assertTrue(engine.taskProgress(player, quest, goal) == 12, "saved ledger retains historical progress");
                player.getStats().setValue(player, Stats.CUSTOM.get(Stats.JUMP), 2);
                phase.set(2);
                helper.assertTrue(false, "wait for a lower native counter");
            }
            if (phase.get() == 2) {
                helper.assertTrue(player.server.getTickCount() % 3 == 0, "sample lower native counter");
                helper.assertTrue(engine.taskProgress(player, quest, goal) == 12, "counter decrease does not subtract latched progress");
                engine.reset(player, quest.id());
                engine.complete(player, quest.id(), true);
                phase.set(3);
                helper.assertTrue(false, "wait for history after reset");
            }
            helper.assertTrue(engine.taskProgress(player, quest, goal) == 2, "reset rereads the remaining native history");
        });
    }

    @GameTest(template = "empty", batch = "statisticCompletion", timeoutTicks = 80)
    @PrefixGameTestTemplate(false)
    public static void rawThresholdAutomaticallyCompletesThroughEngine(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var goal = task("walk", Map.of("stat", "minecraft:walk_one_cm", "count", "100"));
        var quest = install("completion", List.of(goal), false);
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.WALK_ONE_CM), 99);
        var engine = ProgressEngine.get(); engine.reconcile(player);
        var phase = new AtomicInteger();
        helper.succeedWhen(() -> {
            engine.pollTasks(player);
            if (phase.get() == 0) {
                helper.assertTrue(engine.taskProgress(player, quest, goal) == 99, "raw centimeters are read without conversion rounding");
                helper.assertTrue(engine.progress(player).status(quest.id().toString()) != QuestStatus.COMPLETED, "99 centimeters is below one meter");
                player.getStats().setValue(player, Stats.CUSTOM.get(Stats.WALK_ONE_CM), 100);
                phase.set(1);
                helper.assertTrue(false, "wait for threshold crossing");
            }
            helper.assertTrue(engine.progress(player).status(quest.id().toString()) == QuestStatus.COMPLETED,
                    "native counter crosses threshold and normal completion transaction runs");
        });
    }

    private static TaskContext context(net.minecraft.server.level.ServerPlayer player, TaskDefinition task, long progress) {
        return new TaskContext(player, id("book"), id("direct"), ApiViews.task(task), progress);
    }
    private static TaskDefinition task(String name, Map<String, String> config) {
        return new TaskDefinition(id("book"), id(name), StatisticsConfig.ID, config, false);
    }
    private static QuestDefinition install(String name, List<TaskDefinition> tasks, boolean sequential) {
        var behavior = new QuestBehavior(false, false, false, 0, false, false, false,
                DependencyRequirement.ALL_COMPLETED, 0, sequential, false, 0, false);
        var quest = new QuestDefinition(id("book"), id(name), id("chapter"), "Statistics", "", "", "", 0, 0,
                List.of(), tasks, List.of(), "", QuestAppearance.DEFAULT, behavior, Map.of());
        var book = new QuestBookDefinition(id("book"), 1, "Statistics",
                List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, List.of(quest))), Map.of());
        if (!QuestBookManager.get().install(book, new DiagnosticReport())) throw new IllegalStateException("Statistics fixture rejected");
        return quest;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("brnquest_statistics:" + path); }
}

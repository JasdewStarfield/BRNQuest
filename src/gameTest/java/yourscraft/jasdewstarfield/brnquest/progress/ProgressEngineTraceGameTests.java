package yourscraft.jasdewstarfield.brnquest.progress;

import com.mojang.serialization.Codec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.event.*;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.*;
import java.util.function.Consumer;

/** Locks down observable ordering before progress responsibilities move out of the engine. */
@GameTestHolder("brnquest")
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class ProgressEngineTraceGameTests {
    @GameTest(template = "empty", batch = "progressCompletionTrace")
    public static void completionAutoClaimAndDuplicateSubmissionKeepTheirEffectOrder(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        var effects = new TraceEffects();
        var engine = new ProgressEngine(effects);
        var reward = new RewardDefinition(id("book"), id("auto"), id("trace_reward"), Map.of(), "auto_hidden", false);
        var task = new TaskDefinition(id("book"), id("check"), TaskTypes.CHECKMARK, Map.of(), false);
        var quest = quest("quest", List.of(task), List.of(reward));
        var type = rewardType(context -> {
            helper.assertTrue(engine.rewardClaimed(player, reward), "Ordinary execution sees its committed receipt");
            effects.trace.add("execute");
            return RewardResult.success("granted");
        }, Optional.empty());
        try (var ignored = new RewardOverride(reward.typeId(), type)) {
            install(List.of(quest)); engine.reconcile(player);
            effects.observer = event -> {
                if (event instanceof TaskProgressChangedEvent)
                    helper.assertTrue(engine.taskProgress(player, quest, task) == 1, "Task event sees committed progress");
                if (event instanceof QuestCompletedEvent)
                    helper.assertTrue(engine.complete(player, quest.id(), true).code().equals("ALREADY_COMPLETED"), "Reentrant completion cannot repeat effects");
            };
            helper.assertTrue(engine.submitTask(player, quest.id(), task.id(), TaskSubmissionSelection.AUTOMATIC).changed(), "Submission commits");
            helper.assertTrue(effects.trace.equals(List.of("task", "quest", "execute", "reward", "sync:true", "sync:true", "sync:true")),
                    "Completion, auto claim and row response retain their separate syncs: " + effects.trace);
            effects.trace.clear();
            helper.assertTrue(!engine.submitTask(player, quest.id(), task.id(), TaskSubmissionSelection.AUTOMATIC).changed(), "Duplicate is a successful no-op");
            helper.assertTrue(effects.trace.equals(List.of("sync:true")), "Duplicate still sends the row recovery response");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progressRewardTrace")
    public static void ordinaryFailureKeepsReceiptWhileAdvancedOutcomesKeepTheirExistingRetryPolicy(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        var effects = new TraceEffects();
        var engine = new ProgressEngine(effects);
        var ordinary = new RewardDefinition(id("book"), id("ordinary"), id("ordinary_type"), Map.of(), "manual", false);
        var advanced = new RewardDefinition(id("book"), id("advanced"), id("advanced_type"), Map.of(), "manual", false);
        int[] mode = {0};
        var ordinaryType = rewardType(context -> { effects.trace.add("ordinary"); return RewardResult.failure("failed"); }, Optional.empty());
        var advancedType = rewardType(context -> { throw new AssertionError("Advanced handler bypasses ordinary execution"); }, Optional.of(context -> {
            helper.assertTrue(!engine.rewardClaimed(player, advanced), "Advanced attempt has no core receipt yet");
            effects.trace.add("handler");
            return switch (mode[0]) {
                case 0 -> RewardClaimResult.pending("WAIT", "pending");
                case 1 -> RewardClaimResult.failure("DENIED", "failed");
                case 2 -> throw new IllegalStateException("intentional handler exception");
                default -> RewardClaimResult.success("confirmed");
            };
        }));
        try (var ignored = new RewardOverride(ordinary.typeId(), ordinaryType);
             var ignoredAdvanced = new RewardOverride(advanced.typeId(), advancedType)) {
            var quest = quest("rewards", List.of(), List.of(ordinary, advanced));
            install(List.of(quest)); engine.progress(player).status(quest.id().toString(), QuestStatus.COMPLETED);
            helper.assertTrue(engine.claim(player, ordinary.id()).code().equals("EXECUTION_FAILED"), "Ordinary failure reported");
            helper.assertTrue(engine.rewardClaimed(player, ordinary), "Ordinary failure preserves its at-most-once receipt");
            helper.assertTrue(!engine.claim(player, ordinary.id()).changed() && effects.trace.equals(List.of("ordinary")), "Retry never reruns ordinary execution");
            for (int attempt = 0; attempt < 3; attempt++) {
                mode[0] = attempt; effects.trace.clear();
                var result = engine.claim(player, advanced.id());
                helper.assertTrue(!result.changed() && !engine.rewardClaimed(player, advanced), "Unconfirmed handler keeps receipt open");
                helper.assertTrue(effects.trace.equals(attempt == 2 ? List.of("handler") : List.of("handler", "sync:true")),
                        "Pending/failure and thrown handler retain their different response paths: " + effects.trace);
            }
            mode[0] = 3; effects.trace.clear();
            helper.assertTrue(engine.claim(player, advanced.id()).changed(), "Confirmed handler commits");
            helper.assertTrue(effects.trace.equals(List.of("handler", "reward", "sync:true")), "Reward event precedes sync");
            helper.assertTrue(engine.progress(player).status(quest.id().toString()) == QuestStatus.REWARD_CLAIMED, "Both receipts resolve the quest");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progressReentrancyTrace")
    public static void completionDepthLimitUnwindsWithoutLeakingIntoLaterRequests(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var effects = new TraceEffects();
        var engine = new ProgressEngine(effects);
        var quests = new ArrayList<QuestDefinition>();
        for (int i = 0; i < 258; i++) quests.add(quest("chain_" + i, List.of(), List.of()));
        install(quests);
        var results = new ArrayList<OperationResult>();
        int[] next = {1};
        effects.observer = event -> {
            if (event instanceof QuestCompletedEvent && next[0] < quests.size())
                results.add(engine.forceComplete(player, quests.get(next[0]++).id()));
        };
        helper.assertTrue(engine.forceComplete(player, quests.getFirst().id()).changed(), "Initial completion succeeds");
        helper.assertTrue(results.stream().filter(result -> result.code().equals("CHAIN_LIMIT")).count() == 1, "One bounded reentry is rejected");
        helper.assertTrue(effects.trace.stream().filter("quest"::equals).count() == 256, "Exactly the original depth budget commits");
        effects.observer = ignored -> {};
        helper.assertTrue(engine.forceComplete(player, quests.getLast().id()).changed(), "Finally restores the depth for the next request");
        helper.succeed();
    }

    private static final class TraceEffects implements ProgressEffects {
        final List<String> trace = new ArrayList<>();
        Consumer<BrnQuestEvent> observer = ignored -> {};
        public void publish(BrnQuestEvent event) {
            trace.add(event instanceof TaskProgressChangedEvent ? "task" : event instanceof QuestCompletedEvent ? "quest" : "reward");
            observer.accept(event);
        }
        public void sync(ServerPlayer player, boolean changed) { trace.add("sync:" + changed); }
    }

    private static RewardType<Map<String, String>> rewardType(
            java.util.function.Function<RewardContext, RewardResult> execution, Optional<RewardClaimHandler> handler) {
        return new RewardType<>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public Optional<RewardClaimHandler> claimHandler() { return handler; }
            public RewardResult execute(RewardContext context, Map<String, String> config) { return execution.apply(context); }
        };
    }

    /** The frozen public registry stays untouched; fixtures restore the exact prior test-owned entry. */
    private static final class RewardOverride implements AutoCloseable {
        final Map<ResourceLocation, RewardType<?>> types;
        final ResourceLocation id;
        final RewardType<?> previous;
        @SuppressWarnings("unchecked") RewardOverride(ResourceLocation id, RewardType<?> type) throws Exception {
            var field = RewardTypeRegistry.class.getDeclaredField("TYPES"); field.setAccessible(true);
            types = (Map<ResourceLocation, RewardType<?>>) field.get(null); this.id = id; previous = types.put(id, type);
        }
        public void close() { if (previous == null) types.remove(id); else types.put(id, previous); }
    }

    private static QuestDefinition quest(String name, List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id(name), id("chapter"), name, "", "", "", 0, 0, List.of(), tasks, rewards, "");
    }
    private static void install(List<QuestDefinition> quests) {
        var book = new QuestBookDefinition(id("book"), 1, "Progress trace", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, quests)), Map.of());
        if (!QuestBookManager.get().install(book, new DiagnosticReport())) throw new IllegalStateException("Trace fixture book was rejected");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("progress_trace", path); }
}

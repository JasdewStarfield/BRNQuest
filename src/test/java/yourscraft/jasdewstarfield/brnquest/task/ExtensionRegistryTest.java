package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionRegistryTest {
    private static final ResourceLocation COUNTER = id("counter");
    private static final ResourceLocation COUNTER_REWARD = id("counter_reward");

    @BeforeAll
    static void registerTestType() {
        // A foreign namespace exercises the same public path used by a companion mod.
        TaskTypeRegistry.register(COUNTER, new CounterTask());
        RewardTypeRegistry.register(COUNTER_REWARD, new CounterReward());
    }

    @Test
    void registeredForeignTypeDecodesAndSubmitsWithoutEngineBranches() {
        TaskDefinition task = task(Map.of("target", "3"));
        TaskType<?> type = TaskTypeRegistry.get(COUNTER);
        PlayerProgress progress = new PlayerProgress();

        assertNotNull(type);
        assertTrue(type.configError(task).isEmpty());
        assertTrue(type.allowsManualSubmissionDecoded(task));
        assertTrue(type.submitDecoded(null, task, progress).success());
    }

    @Test
    void registeredCodecErrorsReachBookDiagnostics() {
        TaskDefinition invalid = task(Map.of());
        QuestBookDefinition book = book(invalid);
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book, report);

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-119")),
                report.toJson());
        assertFalse(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-117")),
                report.toJson());
    }

    @Test
    void registeredForeignRewardUsesTheSameDecodedExecutionPath() {
        RewardDefinition reward = new RewardDefinition(id("book"), id("counter_reward_instance"), COUNTER_REWARD,
                Map.of("target", "2"), "manual", false);
        RewardType<?> type = RewardTypeRegistry.get(COUNTER_REWARD);

        assertNotNull(type);
        assertTrue(type.configError(reward).isEmpty());
        assertTrue(type.executeDecoded(null, reward).success());
    }

    @Test
    void registeredRewardCodecErrorsReachBookDiagnostics() {
        RewardDefinition reward = new RewardDefinition(id("book"), id("counter_reward_instance"), COUNTER_REWARD,
                Map.of(), "manual", false);
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book(reward), report);

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-120")),
                report.toJson());
        assertFalse(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-118")),
                report.toJson());
    }

    private static TaskDefinition task(Map<String, String> config) {
        return new TaskDefinition(id("book"), id("counter_task"), COUNTER, config, false);
    }

    private static QuestBookDefinition book(TaskDefinition task) {
        return book(List.of(task), List.of());
    }

    private static QuestBookDefinition book(RewardDefinition reward) {
        return book(List.of(), List.of(reward));
    }

    private static QuestBookDefinition book(List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation chapterId = id("chapter");
        QuestDefinition quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "", "", "",
                0, 0, List.of(), tasks, rewards, "quest");
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0, List.of(quest))),
                Map.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_test", path);
    }

    private record CounterConfig(String target) {}

    private static final class CounterTask implements TaskType<CounterConfig> {
        private static final Codec<CounterConfig> CODEC = Codec.STRING.fieldOf("target")
                .xmap(CounterConfig::new, CounterConfig::target).codec();

        public Codec<CounterConfig> configCodec() { return CODEC; }
        public boolean satisfied(ServerPlayer player, TaskDefinition definition, CounterConfig config, PlayerProgress progress) {
            return progress.taskProgress(definition.id().toString()) >= Integer.parseInt(config.target());
        }
        public boolean allowsManualSubmission(CounterConfig config) { return true; }
        public TaskSubmissionResult submit(ServerPlayer player, TaskDefinition definition, CounterConfig config, PlayerProgress progress) {
            return Integer.parseInt(config.target()) > 0
                    ? TaskSubmissionResult.accepted()
                    : TaskSubmissionResult.failure("INVALID_TARGET", "Counter target must be positive");
        }
        public Component describe(TaskDefinition definition, CounterConfig config) {
            return Component.literal("Counter target " + config.target());
        }
    }

    private static final class CounterReward implements RewardType<CounterConfig> {
        public Codec<CounterConfig> configCodec() { return CounterTask.CODEC; }
        public RewardResult execute(ServerPlayer player, RewardDefinition definition, CounterConfig config) {
            return Integer.parseInt(config.target()) > 0
                    ? RewardResult.success("Counter reward executed")
                    : RewardResult.failure("Counter target must be positive");
        }
    }
}

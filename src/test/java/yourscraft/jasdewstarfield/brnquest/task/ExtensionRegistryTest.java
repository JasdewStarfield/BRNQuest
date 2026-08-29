package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeExecutor;
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
        TaskView view = ApiViews.task(task);
        TaskContext context = new TaskContext(null, task.bookId(), id("quest"), view, 0);

        assertNotNull(type);
        assertTrue(TaskTypeExecutor.configError(type, view).isEmpty());
        assertTrue(TaskTypeExecutor.allowsManualSubmission(type, view));
        assertTrue(TaskTypeExecutor.submit(type, context).success());
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
    void itemChoiceCodecRejectsUnknownItemsAndOutOfRangeCounts() {
        String unknownMatcher = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"missing:unknown_item\\\"}\"],\"required\":1}";
        TaskView unknown = new TaskView(id("book"), id("unknown_choice"), TaskTypes.ITEM_CHOICE,
                Map.of("matcher", unknownMatcher, "count", "1"), false);
        String validMatcher = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\"],\"required\":1}";
        TaskView invalidCount = new TaskView(id("book"), id("invalid_count_choice"), TaskTypes.ITEM_CHOICE,
                Map.of("matcher", validMatcher, "count", "0"), false);
        TaskType<?> type = TaskTypeRegistry.get(TaskTypes.ITEM_CHOICE);

        assertTrue(TaskTypeExecutor.configError(type, unknown).isPresent());
        assertTrue(TaskTypeExecutor.configError(type, invalidCount).isPresent());
    }

    @Test
    void registeredForeignRewardUsesTheSameDecodedExecutionPath() {
        RewardDefinition reward = new RewardDefinition(id("book"), id("counter_reward_instance"), COUNTER_REWARD,
                Map.of("target", "2"), "manual", false);
        RewardType<?> type = RewardTypeRegistry.get(COUNTER_REWARD);

        assertNotNull(type);
        assertTrue(RewardTypeExecutor.configError(type, ApiViews.reward(reward)).isEmpty());
        assertTrue(RewardTypeExecutor.execute(type,
                new RewardContext(null, reward.bookId(), id("quest"), ApiViews.reward(reward))).success());
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
        public boolean satisfied(TaskContext context, CounterConfig config) {
            return context.progress() >= Integer.parseInt(config.target());
        }
        public boolean allowsManualSubmission(CounterConfig config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, CounterConfig config) {
            return Integer.parseInt(config.target()) > 0
                    ? TaskSubmissionResult.accepted()
                    : TaskSubmissionResult.failure("INVALID_TARGET", "Counter target must be positive");
        }
        public Component describe(TaskView task, CounterConfig config) {
            return Component.literal("Counter target " + config.target());
        }
    }

    private static final class CounterReward implements RewardType<CounterConfig> {
        public Codec<CounterConfig> configCodec() { return CounterTask.CODEC; }
        public RewardResult execute(RewardContext context, CounterConfig config) {
            return Integer.parseInt(config.target()) > 0
                    ? RewardResult.success("Counter reward executed")
                    : RewardResult.failure("Counter target must be positive");
        }
    }
}

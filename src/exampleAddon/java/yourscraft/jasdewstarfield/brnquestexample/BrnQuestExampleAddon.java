package yourscraft.jasdewstarfield.brnquestexample;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugin;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionResult;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;

import java.util.List;

/**
 * Companion-mod example compiled independently from BRNQuest implementation sources.
 * It deliberately uses only documented public API packages.
 */
@Mod(BrnQuestExampleAddon.MOD_ID)
public final class BrnQuestExampleAddon {
    public static final String MOD_ID = "brnquest_example";
    public static final ResourceLocation MARKER_TASK = id("marker");
    public static final ResourceLocation SIGNAL_TASK = id("signal");
    public static final ResourceLocation CHECKMARK_TASK = id("checkmark");
    public static final ResourceLocation ITEM_TASK = id("item");
    public static final ResourceLocation EXPERIENCE_REWARD = id("experience");
    public static final ResourceLocation GUARDED_TAG_REWARD = id("guarded_tag");
    public static final ResourceLocation PLUGIN_ID = id("core");
    public static final String OBSERVED_TAG = "brnquest_example_observed";

    public BrnQuestExampleAddon() {
        BrnQuestPlugins.register(new ExamplePlugin());
        BrnQuestEvents.subscribe(QuestCompletedEvent.class, BrnQuestExampleAddon::observeCompletion);
        if (FMLEnvironment.dist == Dist.CLIENT) ExampleClientHooks.register();
    }

    /** Demonstrates the same optional companion-owned registration used by BRNTalk. */
    private static final class ExamplePlugin implements BrnQuestPlugin {
        public ResourceLocation id() { return PLUGIN_ID; }
        public void register(BrnQuestExtensionRegistrar registrar) {
            // The companion owns both its sampler and searchable author field source.
            registrar.fieldSource(BrnQuestExampleAddon.id("player_tags"), (player, filter, selected) -> {
                var tags = player.getTags().stream().filter(tag -> tag.contains(filter)).sorted().toList();
                return new ServerFieldSources.Result(tags.stream().map(tag -> new ServerFieldSources.Entry(tag, 1)).toList(),
                        tags.size(), player.getTags().contains(selected) ? 1 : 0, "", "");
            });
            registrar.task(MARKER_TASK, new MarkerTask())
                    .task(SIGNAL_TASK, new SignalTask())
                    .task(CHECKMARK_TASK, new CheckmarkTask())
                    .task(ITEM_TASK, new ExampleItemTask());
            registrar.reward(EXPERIENCE_REWARD, new ExperienceReward())
                    .reward(GUARDED_TAG_REWARD, new GuardedTagReward());
        }
    }

    private static void observeCompletion(QuestCompletedEvent event) {
        // This visible, harmless side effect lets the integration GameTest prove that a
        // companion listener receives immutable public events without internal access.
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var player = server.getPlayerList().getPlayer(event.playerId());
        if (player != null) player.addTag(OBSERVED_TAG);
    }

    private record MarkerConfig(String tag) {}

    private static final class MarkerTask implements TaskType<MarkerConfig> {
        private static final Codec<MarkerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("tag", "brnquest_example_ready").forGetter(MarkerConfig::tag)
        ).apply(instance, MarkerConfig::new));

        public Codec<MarkerConfig> configCodec() { return CODEC; }
        public boolean satisfied(TaskContext context, MarkerConfig config) {
            return context.progress() >= 1 || context.player().getTags().contains(config.tag());
        }
        public int pollingIntervalTicks() { return 10; }
        public long sampledProgress(TaskContext context, MarkerConfig config) {
            // Once observed, the ordinary owner ledger retains the hit until reset.
            return satisfied(context, config) ? 1 : context.progress();
        }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("tag", ConfigValueType.TEXT)
                    .withDefault("brnquest_example_ready")
                    .withHelp("Server-side player tag observed by the passive task").withServerSource(id("player_tags")));
        }
        public Component describe(TaskView task, MarkerConfig config) {
            return Component.literal("Receive server marker " + config.tag());
        }
    }

    private record SignalConfig(String title) {}

    private static final class SignalTask implements TaskType<SignalConfig> {
        private static final Codec<SignalConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("title", "Send signal").forGetter(SignalConfig::title)
        ).apply(instance, SignalConfig::new));

        public Codec<SignalConfig> configCodec() { return CODEC; }
        public boolean satisfied(TaskContext context, SignalConfig config) { return context.progress() >= 1; }
        public boolean allowsManualSubmission(SignalConfig config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, SignalConfig config) {
            return TaskSubmissionResult.accepted();
        }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                    .withDefault("Send signal").withHelp("Objective title"));
        }
        public Component describe(TaskView task, SignalConfig config) { return Component.literal(config.title()); }
    }

    /** Manual confirmation accepts both objective clicks and the public whole-quest intent. */
    private static final class CheckmarkTask implements TaskType<java.util.Map<String, String>> {
        public java.util.Map<String, String> normalizeConfig(
                yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext context,
                java.util.Map<String, String> config) {
            // Return only owned edits: the shared author transaction retains unknown extension fields.
            return config.containsKey("title") ? java.util.Map.of("title", config.get("title").strip()) : java.util.Map.of();
        }

        public Codec<java.util.Map<String, String>> configCodec() {
            return Codec.unboundedMap(Codec.STRING, Codec.STRING);
        }
        public boolean satisfied(TaskContext context, java.util.Map<String, String> config) {
            return context.progress() >= 1;
        }
        public boolean allowsManualSubmission(java.util.Map<String, String> config) { return true; }
        public boolean acceptsQuestCompletionIntent(java.util.Map<String, String> config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, java.util.Map<String, String> config) {
            // Core checks availability and persists completion; the extension never edits its ledger.
            return TaskSubmissionResult.accepted();
        }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                    .withLabel("screen.brnquest_example.field.confirmation_title")
                    .withHelp("screen.brnquest_example.field.confirmation_title.help"));
        }
        public Component describe(TaskView task, java.util.Map<String, String> config) {
            String title = config.getOrDefault("title", "");
            return title.isBlank() ? Component.translatable("screen.brnquest_example.task.checkmark") : Component.literal(title);
        }
    }

    private record ExperienceConfig(int amount) {}

    private static final class ExperienceReward implements RewardType<ExperienceConfig> {
        public java.util.Map<String, String> normalizeConfig(
                yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext context,
                java.util.Map<String, String> config) {
            // This type needs no registry data; other types can resolve resources through context.registries().
            if (!config.containsKey("amount")) return java.util.Map.of();
            int amount = Integer.parseInt(config.get("amount").strip());
            if (amount <= 0) throw new IllegalArgumentException("Experience amount must be positive");
            return java.util.Map.of("amount", Integer.toString(amount));
        }

        // Schema 1 stores every config leaf as text. External codecs should decode
        // that documented wire shape explicitly instead of expecting a JSON number.
        private static final Codec<Integer> STRING_INTEGER = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "Expected an integer, got " + value);
            }
        }, String::valueOf);
        private static final Codec<ExperienceConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                STRING_INTEGER.optionalFieldOf("amount", 3).forGetter(ExperienceConfig::amount)
        ).apply(instance, ExperienceConfig::new));

        public Codec<ExperienceConfig> configCodec() { return CODEC; }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("amount", ConfigValueType.INTEGER)
                    .withDefault("3").withRange(1, Integer.MAX_VALUE)
                    .withHelp("Experience points granted once by the reward ledger"));
        }
        public RewardResult execute(RewardContext context, ExperienceConfig config) {
            context.player().giveExperiencePoints(config.amount());
            return RewardResult.success("Example experience delivered");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

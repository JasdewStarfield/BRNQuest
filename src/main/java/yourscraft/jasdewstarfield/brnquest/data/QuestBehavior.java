package yourscraft.jasdewstarfield.brnquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Runtime-relevant quest behavior after importer inheritance has been resolved. */
public record QuestBehavior(boolean hideUntilDependenciesVisible, boolean hideUntilDependenciesComplete,
                            boolean invisibleUntilComplete, int visibleAfterTasks,
                            boolean hideDetailsUntilStartable, boolean hideTextUntilComplete,
                            boolean hideLockIcon, DependencyRequirement dependencyRequirement,
                            int minimumRequiredDependencies, boolean sequentialTasks,
                            boolean repeatable, int repeatCooldownSeconds,
                            boolean ignoreRewardBlocking) {
    public static final QuestBehavior DEFAULT = new QuestBehavior(false, false, false, 0,
            false, false, false, DependencyRequirement.ALL_COMPLETED, 0,
            false, false, 0, false);

    public static final Codec<QuestBehavior> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("hide_until_dependencies_visible", false).forGetter(QuestBehavior::hideUntilDependenciesVisible),
            Codec.BOOL.optionalFieldOf("hide_until_dependencies_complete", false).forGetter(QuestBehavior::hideUntilDependenciesComplete),
            Codec.BOOL.optionalFieldOf("invisible_until_complete", false).forGetter(QuestBehavior::invisibleUntilComplete),
            Codec.INT.optionalFieldOf("visible_after_tasks", 0).forGetter(QuestBehavior::visibleAfterTasks),
            Codec.BOOL.optionalFieldOf("hide_details_until_startable", false).forGetter(QuestBehavior::hideDetailsUntilStartable),
            Codec.BOOL.optionalFieldOf("hide_text_until_complete", false).forGetter(QuestBehavior::hideTextUntilComplete),
            Codec.BOOL.optionalFieldOf("hide_lock_icon", false).forGetter(QuestBehavior::hideLockIcon),
            Codec.STRING.optionalFieldOf("dependency_requirement", "all_completed")
                    .xmap(DependencyRequirement::parse, DependencyRequirement::serializedName)
                    .forGetter(QuestBehavior::dependencyRequirement),
            Codec.INT.optionalFieldOf("minimum_required_dependencies", 0).forGetter(QuestBehavior::minimumRequiredDependencies),
            Codec.BOOL.optionalFieldOf("sequential_tasks", false).forGetter(QuestBehavior::sequentialTasks),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(QuestBehavior::repeatable),
            Codec.INT.optionalFieldOf("repeat_cooldown_seconds", 0).forGetter(QuestBehavior::repeatCooldownSeconds),
            Codec.BOOL.optionalFieldOf("ignore_reward_blocking", false).forGetter(QuestBehavior::ignoreRewardBlocking)
    ).apply(i, QuestBehavior::new));

    public QuestBehavior {
        dependencyRequirement = dependencyRequirement == null ? DependencyRequirement.ALL_COMPLETED : dependencyRequirement;
        visibleAfterTasks = Math.max(0, visibleAfterTasks);
        minimumRequiredDependencies = Math.max(0, minimumRequiredDependencies);
        repeatCooldownSeconds = Math.max(0, repeatCooldownSeconds);
    }
}

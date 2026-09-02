package yourscraft.jasdewstarfield.brnquest.api;

/** Immutable public projection of behavior that affects availability and completion. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record QuestBehaviorView(boolean hideUntilDependenciesVisible, boolean hideUntilDependenciesComplete,
                                boolean invisibleUntilComplete, int visibleAfterTasks,
                                boolean hideDetailsUntilStartable, boolean hideTextUntilComplete,
                                boolean hideLockIcon, String dependencyRequirement,
                                int minimumRequiredDependencies, boolean sequentialTasks,
                                boolean repeatable, int repeatCooldownSeconds,
                                boolean ignoreRewardBlocking) {}

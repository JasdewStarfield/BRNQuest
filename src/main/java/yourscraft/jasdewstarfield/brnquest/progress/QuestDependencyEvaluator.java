package yourscraft.jasdewstarfield.brnquest.progress;

import yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement;

/** Pure dependency threshold calculation shared by runtime checks and deterministic tests. */
final class QuestDependencyEvaluator {
    private QuestDependencyEvaluator() {}

    static boolean satisfied(DependencyRequirement requirement, int minimum, int total,
                             long completed, long started) {
        if (total == 0) return true;
        int required = minimum > 0 ? minimum : switch (requirement) {
            case ONE_COMPLETED, ONE_STARTED -> 1;
            case ALL_COMPLETED, ALL_STARTED -> total;
        };
        long matched = switch (requirement) {
            case ALL_STARTED, ONE_STARTED -> started;
            case ALL_COMPLETED, ONE_COMPLETED -> completed;
        };
        return required <= total && matched >= required;
    }
}

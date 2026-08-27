package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

/**
 * Client-only interpretation of one task row. The authoritative task ledger is kept separate
 * from a local inventory match so the UI cannot present "ready" as if it were already submitted.
 */
enum TaskDisplayState {
    UNMET,
    READY,
    PENDING,
    SUBMITTED,
    COMPLETED;

    static TaskDisplayState resolve(QuestStatus questStatus, long storedProgress, boolean pending,
                                    boolean interactive, boolean readyForSubmission, boolean editing) {
        if (storedProgress >= 1) return SUBMITTED;
        if (questStatus == QuestStatus.COMPLETED || questStatus == QuestStatus.REWARD_CLAIMED) return COMPLETED;
        boolean questOperable = questStatus == QuestStatus.AVAILABLE || questStatus == QuestStatus.ACTIVE;
        if (!editing && questOperable && pending) return PENDING;
        if (!editing && questOperable && interactive && readyForSubmission) return READY;
        return UNMET;
    }

    boolean confirmed() {
        return this == SUBMITTED || this == COMPLETED;
    }

    boolean actionable() {
        return this == READY;
    }
}

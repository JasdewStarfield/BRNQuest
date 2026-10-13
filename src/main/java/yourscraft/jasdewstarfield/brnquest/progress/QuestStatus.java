package yourscraft.jasdewstarfield.brnquest.progress;

/** Persisted personal quest lifecycle for progress schema 1. */
public enum QuestStatus {
    LOCKED, AVAILABLE, ACTIVE, COMPLETED, REWARD_CLAIMED, UNAVAILABLE;

    /** Tracking is a personal display choice and remains available after the quest's completion or reward receipt. */
    public boolean trackable() {
        return this == AVAILABLE || this == ACTIVE || this == COMPLETED || this == REWARD_CLAIMED;
    }
}

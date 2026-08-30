package yourscraft.jasdewstarfield.brnquest.progress;

/** Bounded administrator intents; no client-supplied progress values are accepted. */
public enum AdminProgressAction {
    FORCE_QUEST, RESET_QUEST, FORCE_TASK, RESET_TASK;

    public boolean taskAction() { return this == FORCE_TASK || this == RESET_TASK; }
    public boolean reset() { return this == RESET_QUEST || this == RESET_TASK; }
}

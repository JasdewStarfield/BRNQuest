package yourscraft.jasdewstarfield.brnquest.network;

import java.util.Optional;

/** Stable wire names, including the review request carried by the mutation envelope. */
enum AuthoringMutationAction {
    UNDO,
    REDO,
    ADD_GROUP,
    UPDATE_GROUP,
    MOVE_GROUP,
    DELETE_GROUP,
    ADD_CHAPTER,
    UPDATE_CHAPTER,
    UPDATE_BOOK_PROPERTIES,
    MOVE_CHAPTER,
    DELETE_CHAPTER,
    ADD_QUEST,
    COPY_QUEST,
    DELETE_QUEST,
    MOVE_QUESTS,
    UPDATE_QUEST_TRANSLATION,
    ADD_DEPENDENCY,
    REMOVE_DEPENDENCY,
    ADD_TASK,
    UPDATE_TASK,
    COPY_TASK,
    MOVE_TASK,
    DELETE_TASK,
    ADD_REWARD,
    UPDATE_REWARD,
    COPY_REWARD,
    MOVE_REWARD,
    DELETE_REWARD,
    REVIEW;

    String wireName() { return name(); }

    /** Exact matching preserves the protocol; unknown names never select a default operation. */
    static Optional<AuthoringMutationAction> fromWire(String action) {
        if (action == null) return Optional.empty();
        try {
            return Optional.of(valueOf(action));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
    /** Families remain explicit so adding an action cannot accidentally route it by name substring. */
    boolean isTypedEntry() {
        return switch (this) {
            case ADD_TASK, UPDATE_TASK, COPY_TASK, MOVE_TASK, DELETE_TASK,
                 ADD_REWARD, UPDATE_REWARD, COPY_REWARD, MOVE_REWARD, DELETE_REWARD -> true;
            default -> false;
        };
    }
}

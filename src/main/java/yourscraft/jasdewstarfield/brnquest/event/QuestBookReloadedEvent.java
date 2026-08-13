package yourscraft.jasdewstarfield.brnquest.event;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.QuestBookView;

/** Read-only notification emitted only after a valid task-book snapshot replaces the old one. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record QuestBookReloadedEvent(QuestBookView previousBook, QuestBookView currentBook)
        implements BrnQuestEvent {}

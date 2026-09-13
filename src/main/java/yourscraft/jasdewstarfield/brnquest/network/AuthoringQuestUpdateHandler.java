package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.UUID;

/** Server-authoritative authoring use cases; requests are parsed before entering this boundary. */
final class AuthoringQuestUpdateHandler {
    private final ServerPlayer player;
    private final AuthoringResponseSender responses;

    AuthoringQuestUpdateHandler(ServerPlayer player) { this(player, AuthoringResponseSender.forPlayer(player)); }

    /** Keeps real service execution independent from how the response is delivered. */
    AuthoringQuestUpdateHandler(ServerPlayer player, AuthoringResponseSender responses) {
        this.player = player;
        this.responses = responses;
    }

    void update(AuthoringRequestDecoder.QuestRequest wire) {
        UUID sessionId = wire.sessionId();
        ResourceLocation bookId = wire.bookId();
        ResourceLocation questId = wire.questId();
        ResourceLocation replacementQuestId = wire.replacementQuestId();
        AuthorOperationResult<DraftSnapshot> current = EditSessionService.get().snapshot(player, sessionId,
                bookId, wire.draftRevision());
        QuestDefinition quest = current.success() ? current.value().book().quests().stream()
                .filter(candidate -> candidate.id().equals(questId)).findFirst().orElse(null) : null;
        if (!current.success() || quest == null) {
            responses.sendFailure("UPDATE", current.success() ? AuthorOperationResult.Status.NOT_FOUND : current.status(),
                    current.success() ? "QUEST_NOT_FOUND" : current.code(),
                    current.success() ? "Selected quest no longer exists" : current.message());
            return;
        }
        boolean hasX = wire.x() != null;
        boolean hasY = wire.y() != null;
        if (!replacementQuestId.equals(questId) && hasX
                && (Double.compare(wire.x(), quest.x()) != 0 || Double.compare(wire.y(), quest.y()) != 0)) {
            responses.sendFailure("UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "POSITION_WITH_RENAME", "Rename the quest before editing its coordinates");
            return;
        }
        String icon = quest.icon();
        if (!wire.preserveIcon()) {
            ResourceLocation iconId = wire.iconId();
            if (wire.iconKind() == AuthoringRequestDecoder.IconKind.ITEM) {
                if (iconId != null && !BuiltInRegistries.ITEM.containsKey(iconId)) {
                    responses.sendFailure("UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                            "INVALID_ICON_ITEM", "Icon item must be a registered item ID");
                    return;
                }
                icon = iconId == null ? "" : "{id:\"" + iconId + "\",count:1}";
            } else {
                icon = iconId == null ? "" : QuestIconValue.texture(iconId);
            }
        }
        double replacementX = hasX ? wire.x() : quest.x();
        double replacementY = hasY ? wire.y() : quest.y();
        var appearanceInput = wire.appearance();
        QuestAppearance appearance = appearanceInput == null ? quest.appearance() : new QuestAppearance(
                appearanceInput.shape(), appearanceInput.size(), appearanceInput.iconScale(), appearanceInput.minWidth(), quest.appearance().hideDependencyLines());
        if (wire.hideDependencyLines() != null) appearance = new QuestAppearance(appearance.shape(), appearance.size(), appearance.iconScale(), appearance.minWidth(),
                wire.hideDependencyLines().equals("default") ? null : Boolean.valueOf(wire.hideDependencyLines()));
        var behaviorInput = wire.behavior();
        QuestBehavior behavior = behaviorInput == null ? quest.behavior() : new QuestBehavior(
                behaviorInput.hideUntilDependenciesVisible(), behaviorInput.hideUntilDependenciesComplete(),
                behaviorInput.invisibleUntilComplete(), behaviorInput.visibleAfterTasks(), behaviorInput.hideDetailsUntilStartable(),
                behaviorInput.hideTextUntilComplete(), behaviorInput.hideLockIcon(), behaviorInput.dependencyRequirement(),
                behaviorInput.minimumRequiredDependencies(), behaviorInput.sequentialTasks(), behaviorInput.repeatable(),
                behaviorInput.repeatCooldownSeconds(), behaviorInput.ignoreRewardBlocking());
        QuestDefinition replacement = new QuestDefinition(quest.bookId(), replacementQuestId, quest.chapterId(),
                wire.title(), wire.subtitle(), wire.description(), icon, replacementX, replacementY,
                quest.dependencies(), quest.tasks(), quest.rewards(), quest.legacyId(), appearance, behavior, quest.extensions());
        // Same-ID property saves may atomically update exact coordinates. Renames keep the dedicated
        // alias-migration path, while legacy/quick-text callers omit coordinates and preserve position.
        var updated = replacementQuestId.equals(questId) && hasX
                ? AuthorApi.editor().updateQuest(player, sessionId, bookId, wire.draftRevision(), questId, replacement)
                : AuthorApi.editor().updateQuestBasics(player, sessionId, bookId, wire.draftRevision(), questId,
                replacement);
        if (!updated.success()) {
            String message = updated.message();
            if (updated.value() != null && !updated.value().diagnostics().isEmpty()) {
                var first = updated.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.message();
            }
            responses.sendFailure("UPDATE", updated.status(), updated.code(), message);
            return;
        }
        DraftSnapshot draft = updated.value().snapshot();
        var renewed = AuthorApi.renew(player, sessionId, draft.draftRevision());
        if (!renewed.success()) {
            responses.sendFailure("UPDATE", renewed);
            return;
        }
        // Property completion updates the authoritative session; the visible Save
        // control is the explicit boundary that persists the accumulated draft.
        sendDraft("UPDATE", "QUEST_UPDATED_UNSAVED",
                "Quest properties updated; save the draft to persist them", renewed.value(), draft);
    }

    /** Closing an oversized session is a use-case decision supplied to the transport explicitly. */
    private void sendDraft(String action, String code, String message,
                                  EditSessionHandle handle, DraftSnapshot draft) {
        responses.sendDraft(action, code, message, handle, draft,
                () -> AuthorApi.close(player, handle.sessionId(), handle.session().draftRevision()));
    }
}

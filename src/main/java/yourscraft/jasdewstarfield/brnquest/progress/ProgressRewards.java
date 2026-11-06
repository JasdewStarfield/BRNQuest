package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.event.RewardClaimedEvent;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.reward.RewardClaimContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardClaimResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.*;

/** Executes ordinary and advanced reward policies inside the engine owner transaction. */
final class ProgressRewards {
    interface Host {
        OperationResult claim(ServerPlayer player, ResourceLocation rewardId, boolean notifyAutomatic);
        void claimCommitted(ServerPlayer player, QuestDefinition quest, PlayerProgress progress);
    }
    private final ProgressQueries queries;
    private final ProgressEffects effects;
    private final Host host;

    ProgressRewards(ProgressQueries queries, ProgressEffects effects, Host host) {
        this.queries = queries; this.effects = effects; this.host = host;
    }

    static boolean automaticAllowed(RewardDefinition reward) {
        var book = QuestBookManager.get().active().orElse(null);
        if (book == null || book.book().settings().suppressAutoClaim()) return false;
        var type = RewardTypeRegistry.get(reward.typeId());
        return reward.policy().automatic() && type != null && !type.requiresManualClaim(reward.config());
    }

    void deliverAutomatic(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        for (QuestDefinition quest : snapshot.book().quests()) {
            QuestStatus status = queries.progress(player).status(quest.id().toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) {
                for (RewardDefinition reward : quest.rewards()) if (automaticAllowed(reward)
                        && !queries.rewardClaimed(player, reward)) host.claim(player, reward.id(), reward.policy().notifyPlayer());
            }
        }
    }

    OperationResult claimLocked(ServerPlayer player, ResourceLocation rewardId, boolean notifyAutomatic) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return OperationResult.failure("NO_BOOK", "No active book");
        QuestDefinition owner = snapshot.book().quests().stream().filter(q -> q.rewards().stream().anyMatch(r -> r.id().equals(rewardId))).findFirst().orElse(null);
        if (owner == null) return OperationResult.failure("NOT_FOUND", "Unknown reward");
        PlayerProgress progress = queries.progress(player);
        if (progress.status(owner.id().toString()).ordinal() < QuestStatus.COMPLETED.ordinal()) return OperationResult.failure("LOCKED", "Quest is incomplete");
        RewardDefinition reward = owner.rewards().stream().filter(r -> r.id().equals(rewardId)).findFirst().orElseThrow();
        if (queries.rewardClaimed(player, reward))
            return OperationResult.noChange("ALREADY_CLAIMED", "Reward already claimed");
        if (queries.shared(player) && !progress.completionMembers(owner.id().toString()).contains(player.getUUID())) {
            return OperationResult.failure("NOT_ELIGIBLE", "Player was not a member when this cycle completed");
        }
        RewardType<?> type = RewardTypeRegistry.get(reward.typeId());
        if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown reward type");
        var execution = new RewardContext(player, owner.bookId(), owner.id(), ApiViews.reward(reward));
        var handler = type.claimHandler();
        QuestProgressData data = QuestProgressData.get(player.getServer());
        // Full reset can distinguish an unresolved attempt from an already empty quest.
        progress.rewardAttempted(owner.id().toString());
        data.setDirty();
        String message;
        if (handler.isPresent()) {
            // Advanced types own attempt persistence; only a confirmed success commits the core ledger.
            RewardClaimResult result;
            try {
                result = java.util.Objects.requireNonNull(handler.orElseThrow().claim(new RewardClaimContext(
                        execution, ProgressOwnerService.require(player), progress.completionCycles(owner.id().toString()), progress.claimGeneration(owner.id().toString()))));
            } catch (Exception error) {
                yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.error("Reward claim handler failed for {} ({})", rewardId, reward.typeId(), error);
                return OperationResult.failure("CLAIM_HANDLER_FAILED", "Reward attempt failed; consult the type's recovery policy");
            }
            if (result.state() != RewardClaimResult.State.SUCCESS) {
                effects.sync(player, true);
                return result.state() == RewardClaimResult.State.PENDING
                        ? OperationResult.noChange(result.code(), result.message())
                        : OperationResult.failure(result.code(), result.message());
            }
            message = result.message();
            if (queries.shared(player) && !reward.teamReward()) progress.claimMember(player.getUUID(), rewardId.toString());
            else progress.claim(rewardId.toString());
            data.setDirty();
        } else {
            // Preserve the existing ordinary-reward policy for extensions that only implement execute.
            if (queries.shared(player) && !reward.teamReward()) progress.claimMember(player.getUUID(), rewardId.toString());
            else progress.claim(rewardId.toString());
            data.setDirty();
            var result = RewardTypeExecutor.execute(type, execution);
            if (!result.success()) return OperationResult.failure("EXECUTION_FAILED", result.message());
            message = result.message();
        }
        if (queries.rewardsResolved(owner, progress)) progress.status(owner.id().toString(), QuestStatus.REWARD_CLAIMED);
        effects.publish(new RewardClaimedEvent(player.getUUID(), player.getScoreboardName(),
                snapshot.book().id(), owner.id(), rewardId, ApiViews.reward(reward),
                BrnQuestApi.getProgress(player, owner.id().toString()).orElseThrow()));
        host.claimCommitted(player, owner, progress);
        effects.sync(player, true);
        if (notifyAutomatic) {
            player.displayClientMessage(Component.translatable("message.brnquest.reward.auto_claimed",
                    rewardId.toString()), true);
        }
        return OperationResult.success(message);

    }

    /** Completion uses its original authored reward order and re-enters the same protected claim entry. */
    void deliverCompleted(ServerPlayer player, QuestDefinition quest) {
        quest.rewards().stream().filter(ProgressRewards::automaticAllowed)
                .forEach(reward -> host.claim(player, reward.id(), reward.policy().notifyPlayer()));
    }
}

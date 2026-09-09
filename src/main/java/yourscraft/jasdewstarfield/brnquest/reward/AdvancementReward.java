package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.task.advancement.*;
import java.util.*;

/** Vanilla award supplies its own completion idempotence and native reward/function side effects. */
public final class AdvancementReward implements RewardType<Map<String,String>> {
    private static final Set<RewardContext> IN_FLIGHT = new HashSet<>();
    public Optional<RewardClaimHandler> claimHandler() {
        return Optional.of(claim -> {
            var context = claim.rewardContext();
            var result = execute(context, context.reward().config());
            return result.success() ? RewardClaimResult.success(result.message())
                    : RewardClaimResult.failure("ADVANCEMENT_GRANT_FAILED", result.message());
        });
    }
    public Codec<Map<String,String>> configCodec() { return AdvancementConfig.CODEC; }
    public List<ConfigFieldDescriptor> configFields() { return AdvancementConfig.fields(false); }
    public Map<String,String> normalizeConfig(Map<String,String> values) { return AdvancementConfig.normalize(values); }
    public RewardResult execute(RewardContext context, Map<String,String> values) {
        // A vanilla reward function can call a BRNQuest command; reject reentry until this grant returns.
        if (!IN_FLIGHT.add(context)) return RewardResult.failure("Advancement reward is already executing");
        try {
            var config = AdvancementConfig.parse(values);
            final java.util.List<net.minecraft.advancements.AdvancementHolder> holders;
            try { holders = AdvancementTargets.resolve(context.player().server, config); }
            catch (IllegalArgumentException error) { return RewardResult.failure(error.getMessage()); }
            for (var holder : holders) {
                // Never grant parents implicitly. Already granted criteria are native no-ops.
                var criteria = config.criterion().isEmpty() ? holder.value().criteria().keySet() : Set.of(config.criterion());
                for (String criterion : criteria) context.player().getAdvancements().award(holder, criterion);
            }
            return RewardResult.success("Advancement granted");
        } finally { IN_FLIGHT.remove(context); }
    }
}

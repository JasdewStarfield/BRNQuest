package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;

import java.util.Optional;

/** Internal generic bridge that keeps decoding helpers out of the public RewardType contract. */
@ApiStatus(ApiStability.INTERNAL)
public final class RewardTypeExecutor {
    private RewardTypeExecutor() {}

    public static Optional<String> configError(RewardType<?> type, RewardView reward) {
        return decode(type, reward).error().map(error -> error.message());
    }

    public static RewardResult execute(RewardType<?> type, RewardContext context) {
        return executeTyped(type, context);
    }

    private static <T> RewardResult executeTyped(RewardType<T> type, RewardContext context) {
        return StringMapConfigCodec.decode(type.configCodec(), context.reward().config()).result()
                .map(config -> type.execute(context, config))
                .orElseGet(() -> RewardResult.failure("Reward configuration is invalid"));
    }

    private static <T> com.mojang.serialization.DataResult<T> decode(RewardType<T> type, RewardView reward) {
        return StringMapConfigCodec.decode(type.configCodec(), reward.config());
    }
}

package legacy;

import com.mojang.serialization.Codec;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import java.util.Map;

/** Retains only the legacy normalization signature when compiled. */
public final class LegacyRewardType implements RewardType<Map<String, String>> {
    public int calls;
    public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
    public Map<String, String> normalizeConfig(Map<String, String> config) { calls++; return Map.of("legacy", "reward"); }
    public RewardResult execute(RewardContext context, Map<String, String> config) { return RewardResult.success("Legacy"); }
}

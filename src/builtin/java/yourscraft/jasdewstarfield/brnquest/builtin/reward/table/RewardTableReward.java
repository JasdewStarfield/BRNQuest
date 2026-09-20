package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import java.util.*;

/** Unified, bounded all/random/choice trees; the root coordinator exclusively owns their execution. */
public final class RewardTableReward implements RewardType<Map<String,String>> {
    public static final ResourceLocation ID = ResourceLocation.parse("brnquest:reward_table");
    public Codec<Map<String,String>> configCodec() {
        return Codec.unboundedMap(Codec.STRING, Codec.STRING).validate(values -> {
            try {
                var tree = RewardTableTree.parse(values.get("table"));
                validateTree(tree.document(), "root");
                return DataResult.success(values);
            } catch (RuntimeException error) { return DataResult.error(() -> Objects.toString(error.getMessage(), "Invalid reward table")); }
        });
    }
    private static void validateTree(com.google.gson.JsonObject table,String parent) {
        for(var value:table.getAsJsonArray("entries")) {
            var entry=value.getAsJsonObject();String path=parent+"/"+entry.get("entry_id").getAsString();
            if(entry.has("table")) { validateTree(entry.getAsJsonObject("table"),path);continue; }
            var type=RewardTypeRegistry.get(ResourceLocation.parse(entry.get("type").getAsString()));
            if(type==null || type.composition().isEmpty())throw new IllegalArgumentException(path+": unsupported composition type");
            try {
                type.composition().orElseThrow().validateConfig(RewardTableTree.config(entry));
                StringMapConfigCodec.decode(type.configCodec(),RewardTableTree.config(entry)).getOrThrow();
            } catch(RuntimeException error) { throw new IllegalArgumentException(path+": "+error.getMessage(),error); }
        }
    }
    private static boolean hasChoice(com.google.gson.JsonObject table) {
        if(table.has("mode") && table.get("mode").getAsString().equals("choice"))return true;
        for(var value:table.getAsJsonArray("entries")) {
            var entry=value.getAsJsonObject();if(entry.has("table") && hasChoice(entry.getAsJsonObject("table")))return true;
        }
        return false;
    }
    public List<ConfigFieldDescriptor> configFields() {
        // The outer reward owns its display name; table JSON remains execution configuration only.
        return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                .withLabel("screen.brnquest.editor.config.title"),
                ConfigFieldDescriptor.field("table", ConfigValueType.TEXT).asRequired()
                .withLabel("screen.brnquest.reward_table.entries").withHelp("screen.brnquest.reward_table.help"));
    }
    public void clientClaimResponse(net.minecraft.server.level.ServerPlayer player,
            yourscraft.jasdewstarfield.brnquest.api.RewardView reward, yourscraft.jasdewstarfield.brnquest.api.OperationResult result) {
        if (result.code().equals("TABLE_AWAITING_CHOICE")) yourscraft.jasdewstarfield.brnquest.builtin.network.RewardTableChoiceNetwork.open(player, reward.id());
    }
    public Optional<RewardClaimHandler> claimHandler() { return Optional.of(RewardTableService::claim); }
    public boolean requiresManualClaim(Map<String,String> config) {
        try { return hasChoice(RewardTableTree.parse(config.get("table")).document()); }
        catch (RuntimeException invalid) { return true; }
    }
    public RewardResult execute(RewardContext context, Map<String,String> config) {
        return RewardResult.failure("Reward tables require the authoritative claim coordinator");
    }
}

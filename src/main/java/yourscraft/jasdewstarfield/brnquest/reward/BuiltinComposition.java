package yourscraft.jasdewstarfield.brnquest.reward;

import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementConfig;
import yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementTargets;
import java.util.*;

/** Built-in opt-in adapters. The original top-level execution policies remain untouched. */
final class BuiltinComposition implements ComposableReward {
    private final String kind;
    BuiltinComposition(String kind) { this.kind = kind; }
    public void validateConfig(Map<String,String> values) {
        if (kind.equals("xp") || kind.equals("xp_levels")) {
            if (Integer.parseInt(values.getOrDefault(kind, "1")) < 1) throw new IllegalArgumentException("Experience must be positive");
        } else if (kind.equals("item")) {
            int multiplier = Integer.parseInt(values.getOrDefault("count", "1"));
            if (multiplier < 1) throw new IllegalArgumentException("Item multiplier must be positive");
            try {
                var tag = TagParser.parseTag(values.getOrDefault("item", ""));
                int amount = tag.contains("count") ? tag.getInt("count") : 1;
                if (amount < 1 || Math.multiplyExact(amount, multiplier) > 4096) throw new IllegalArgumentException("Item delivery must be 1..4096");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) { throw new IllegalArgumentException("Invalid item SNBT", error); }
        }
    }
    private static RewardDefinition definition(RewardLeafContext leaf, Map<String,String> config) {
        var root = leaf.root().rewardContext();
        // Keep the real root ID for diagnostics only; occurrenceId is the sole child receipt identity.
        return new RewardDefinition(root.bookId(), root.reward().id(), leaf.typeId(), config, "manual", false);
    }
    public Map<String,String> prepare(RewardLeafContext leaf) throws Exception {
        validateConfig(leaf.config());
        var player = leaf.root().rewardContext().player();
        Map<String,String> values = new LinkedHashMap<>(leaf.config());
        switch (kind) {
            case "item" -> {
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), TagParser.parseTag(values.get("item")));
                int multiplier = Integer.parseInt(values.getOrDefault("count", "1"));
                if (stack.isEmpty() || multiplier < 1) throw new IllegalArgumentException("Invalid item or multiplier");
                int count = Math.multiplyExact(stack.getCount(), multiplier);
                // Bound inventory/drop work independently of the number of configured leaves.
                if (count > 4096) throw new IllegalArgumentException("Item delivery exceeds 4096 items per leaf");
            }
            case "xp", "xp_levels" -> {
                if (Integer.parseInt(values.getOrDefault(kind, "1")) < 1) throw new IllegalArgumentException("Experience must be positive");
            }
            case "advancement" -> {
                var targets = AdvancementTargets.resolve(player.server, AdvancementConfig.parse(values));
                values.put("brnquest.prepared_targets", targets.stream().map(h -> h.id().toString()).sorted().collect(java.util.stream.Collectors.joining(",")));
            }
            case "command" -> {
                var prepared = CommandRewardService.prepare(player, definition(leaf, values));
                values.put("brnquest.prepared_command", prepared.command());
                var pos = player.position();
                values.put("brnquest.prepared_position", pos.x + "," + pos.y + "," + pos.z);
                values.put("brnquest.prepared_dimension", player.level().dimension().location().toString());
                var rotation = prepared.source().getRotation();
                values.put("brnquest.prepared_rotation", rotation.x + "," + rotation.y);
            }
            default -> { /* Built-in custom explicitly acknowledges without executing a script. */ }
        }
        return Map.copyOf(values);
    }
    public RewardClaimResult execute(RewardLeafContext leaf, Map<String,String> prepared) throws Exception {
        var root = leaf.root().rewardContext();
        if (kind.equals("command")) {
            var command = CommandRewardService.prepare(root.player(), definition(leaf, leaf.config()));
            String[] xyz = prepared.get("brnquest.prepared_position").split(",");
            var level = root.player().server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(prepared.get("brnquest.prepared_dimension"))));
            if (level == null) throw new IllegalArgumentException("Prepared command dimension is unavailable");
            var source = command.source().withLevel(level).withPosition(new net.minecraft.world.phys.Vec3(
                    Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2])));
            // Local ^ coordinates depend on rotation as well as position; reconnecting must not reinterpret them.
            String[] rotation = prepared.get("brnquest.prepared_rotation").split(",");
            source = source.withRotation(new net.minecraft.world.phys.Vec2(Float.parseFloat(rotation[0]), Float.parseFloat(rotation[1])));
            command = new CommandRewardService.Prepared(command.config(), prepared.get("brnquest.prepared_command"), source, root.reward().id());
            CommandRewardService.journal(root.player()).begin(leaf.occurrenceId(), command.command());
            return CommandRewardService.execute(root.player(), leaf.occurrenceId(), command, () -> {});
        }
        var context = new RewardContext(root.player(), root.bookId(), root.questId(), ApiViews.reward(definition(leaf, prepared)));
        var result = RewardTypeExecutor.execute(RewardTypeRegistry.get(leaf.typeId()), context);
        return result.success() ? RewardClaimResult.success(result.message()) : RewardClaimResult.failure("UNKNOWN", result.message());
    }
    public RewardClaimResult recover(RewardLeafContext leaf, Map<String,String> prepared) throws Exception {
        if (!kind.equals("command")) return ComposableReward.super.recover(leaf, prepared);
        var receipt = CommandRewardService.journal(leaf.root().rewardContext().player()).read(leaf.occurrenceId());
        if (receipt != null && Set.of("REPORTED_SUCCESS", "ACKNOWLEDGED").contains(receipt.outcome().state()))
            return RewardClaimResult.success("Composed command reconciled without replay");
        return RewardClaimResult.failure("UNKNOWN", "Command has no durable success receipt; review required");
    }
}

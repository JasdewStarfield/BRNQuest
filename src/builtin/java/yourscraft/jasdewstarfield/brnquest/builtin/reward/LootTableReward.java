package yourscraft.jasdewstarfield.brnquest.builtin.reward;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableService;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native loot is generated once per selected occurrence, serialized before delivery, and never preview-rolled. */
public final class LootTableReward implements RewardType<Map<String, String>>, ComposableReward {
    public static final ResourceLocation ID = ResourceLocation.parse("brnquest:loot_table");
    private static final String ITEMS = "brnquest.loot_items";
    private static final int MAX_STACKS = 1024, MAX_ITEMS = 4096, MAX_BYTES = 512 * 1024;

    public Codec<Map<String, String>> configCodec() {
        return Codec.unboundedMap(Codec.STRING, Codec.STRING).validate(config -> {
            try { validateConfig(config); return DataResult.success(config); }
            catch (RuntimeException error) { return DataResult.error(() -> error.getMessage()); }
        });
    }
    public void validateConfig(Map<String, String> config) {
        if (config.getOrDefault("loot_table", "").isBlank() || ResourceLocation.tryParse(config.getOrDefault("loot_table", "")) == null)
            throw new IllegalArgumentException("A single valid loot table ID is required");
    }
    public Map<String, String> normalizeConfig(Map<String, String> config) {
        var normalized = new LinkedHashMap<>(config);
        normalized.computeIfPresent("loot_table", (key, value) -> value.trim());
        return Map.copyOf(normalized);
    }
    public List<ConfigFieldDescriptor> configFields() {
        return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.title"),
                ConfigFieldDescriptor.field("loot_table", ConfigValueType.RESOURCE_LOCATION).asRequired()
                        .withServerSource(ID).withLabel("screen.brnquest.loot_table.id").withHelp("screen.brnquest.loot_table.help"));
    }
    public Optional<ComposableReward> composition() { return Optional.of(this); }
    public Optional<RewardClaimHandler> claimHandler() { return Optional.of(RewardTableService::claimSingle); }
    public RewardResult execute(RewardContext context, Map<String, String> config) {
        return RewardResult.failure("Native loot requires the authoritative claim coordinator");
    }

    /** Missing tables must not be confused with Minecraft's legitimate empty table fallback. */
    private static LootTable resolve(ServerPlayer player, Map<String, String> config) {
        var id = ResourceLocation.parse(config.get("loot_table"));
        var registry = player.server.reloadableRegistries().get().registryOrThrow(Registries.LOOT_TABLE);
        if (!registry.containsKey(id)) throw new IllegalArgumentException("Missing loot table: " + id);
        return registry.getOrThrow(ResourceKey.create(Registries.LOOT_TABLE, id));
    }
    private static LootParams parameters(ServerPlayer player, LootTable table) {
        // A quest reward is not a kill or block-break event: never invent a victim, damage source or tool.
        var supplied = Set.of(LootContextParams.ORIGIN, LootContextParams.THIS_ENTITY);
        if (!supplied.containsAll(table.getParamSet().getRequired()))
            throw new IllegalArgumentException("Loot table requires unavailable context: " + table.getParamSet().getRequired());
        return new LootParams.Builder(player.serverLevel()).withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck()).create(table.getParamSet());
    }
    public Map<String, String> prepare(RewardLeafContext context) {
        validateConfig(context.config());
        var player = context.root().rewardContext().player();
        var table = resolve(player, context.config());
        parameters(player, table);
        // Native validation also checks references and parameter usage inside conditions/functions.
        var problems = new net.minecraft.util.ProblemReporter.Collector();
        var available = new net.minecraft.world.level.storage.loot.parameters.LootContextParamSet.Builder()
                .required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build();
        table.validate(new net.minecraft.world.level.storage.loot.ValidationContext(problems, available,
                player.server.reloadableRegistries().lookup()));
        if (!problems.get().isEmpty()) throw new IllegalArgumentException("Unsupported loot table context/references: " + problems.get());
        return context.config();
    }
    public Map<String, String> freeze(RewardLeafContext context) {
        prepare(context);
        var player = context.root().rewardContext().player();
        var table = resolve(player, context.config());
        // Use the native non-raw route, including NeoForge global loot modifiers and item components.
        var stacks = table.getRandomItems(parameters(player, table));
        var encoded = new com.google.gson.JsonArray();
        long total = 0;
        for (var stack : stacks) {
            if (stack.isEmpty()) continue;
            total += stack.getCount();
            if (encoded.size() >= MAX_STACKS || total > MAX_ITEMS) throw new IllegalArgumentException("Loot delivery exceeds 1024 stacks or 4096 items");
            encoded.add(stack.save(player.registryAccess()).toString());
        }
        String snapshot = encoded.toString();
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Loot snapshot exceeds 512 KiB");
        var result = new LinkedHashMap<>(context.config()); result.put(ITEMS, snapshot);
        return Map.copyOf(result);
    }
    public RewardClaimResult execute(RewardLeafContext context, Map<String, String> prepared) throws Exception {
        var player = context.root().rewardContext().player();
        String snapshot = Objects.requireNonNull(prepared.get(ITEMS), "Missing frozen loot");
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Oversized frozen loot");
        var encoded = com.google.gson.JsonParser.parseString(snapshot).getAsJsonArray();
        if (encoded.size() > MAX_STACKS) throw new IllegalArgumentException("Too many frozen stacks");
        var stacks = new ArrayList<ItemStack>(); long total = 0;
        // Decode and validate every stack before the first inventory mutation.
        for (var value : encoded) {
            var stack = ItemStack.parseOptional(player.registryAccess(), TagParser.parseTag(value.getAsString()));
            if (stack.isEmpty() || !stack.isItemEnabled(player.serverLevel().enabledFeatures()))
                throw new IllegalArgumentException("Frozen loot item is unavailable");
            total += stack.getCount();
            if (total > MAX_ITEMS) throw new IllegalArgumentException("Too many frozen items");
            stacks.add(stack);
        }
        for (var stack : stacks) ItemRewardDelivery.deliver(player, stack);
        return RewardClaimResult.success(stacks.isEmpty() ? "Empty loot result consumed" : "Frozen loot delivered");
    }
    /** Search only registered IDs. Listing and previewing a table never consumes random state. */
    public static void registerFieldSource(yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar registrar) {
        registrar.fieldSource(ID, (player, filter, selected) -> {
            var ids = player.server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream()
                    .map(ResourceLocation::toString).sorted().toList();
            var matching = ids.stream().filter(id -> id.contains(filter.toLowerCase(Locale.ROOT)))
                    .map(id -> new ServerFieldSources.Entry(id, 1)).toList();
            return new ServerFieldSources.Result(matching, matching.size(), ids.contains(selected) ? 1 : 0,
                    selected.isBlank() || ids.contains(selected) ? "" : "missing", "");
        });
    }
}

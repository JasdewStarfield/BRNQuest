package yourscraft.jasdewstarfield.brnquest.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Side-neutral canonical model for every built-in item objective. A one-entry objective is
 * the former single-item task; multiple entries and the legacy tag mode use the same plan.
 * The server computes the complete removal plan before mutating an inventory.
 */
@ApiStatus(ApiStability.INTERNAL)
public final class ItemChoiceMatcher {
    public static final int MAX_CANDIDATES = 32;

    private ItemChoiceMatcher() {}

    public enum EntryKind { ITEM, TAG }

    /** One independently editable accepted entry and its own required quantity. */
    public record Entry(EntryKind kind, String value, int requiredCount) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            value = Objects.requireNonNull(value, "value");
            if (value.isBlank()) throw new IllegalArgumentException("Item entry value is required");
            if (requiredCount < 1) throw new IllegalArgumentException("Item entry count must be positive");
        }

        public static Entry item(String itemSnbt, int count) {
            return new Entry(EntryKind.ITEM, canonicalSnbt(itemSnbt), count);
        }

        public static Entry tag(ResourceLocation tagId, int count) {
            return new Entry(EntryKind.TAG, Objects.requireNonNull(tagId, "tagId").toString(), count);
        }
    }

    /** Target-level completion count plus its ordered child item requirements. */
    public record Spec(List<Entry> entries, int requiredEntries) {
        public Spec {
            entries = List.copyOf(entries);
            if (entries.isEmpty() || entries.size() > MAX_CANDIDATES) {
                throw new IllegalArgumentException("Item entry count must be between 1 and " + MAX_CANDIDATES);
            }
            if (requiredEntries < 1 || requiredEntries > entries.size()) {
                throw new IllegalArgumentException("Required entries must be between 1 and item entry count");
            }
            long uniqueEntries = entries.stream().map(ItemChoiceMatcher::entryIdentity)
                    .distinct().count();
            if (uniqueEntries != entries.size()) {
                throw new IllegalArgumentException("Item objective contains duplicate entries");
            }
            // A tag is one alternative group. Mixing it with exact entries would require
            // allocating overlapping inventory types and is deliberately deferred.
            if (entries.stream().anyMatch(entry -> entry.kind() == EntryKind.TAG) && entries.size() != 1) {
                throw new IllegalArgumentException("A tag entry cannot currently be mixed with other entries");
            }
        }

        public Spec withRequiredEntries(int required) {
            return new Spec(entries, required);
        }

        public String encode() {
            JsonObject json = new JsonObject();
            json.addProperty("version", 2);
            JsonArray encodedEntries = new JsonArray();
            for (Entry entry : entries) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("kind", entry.kind() == EntryKind.ITEM ? "item" : "tag");
                encoded.addProperty(entry.kind() == EntryKind.ITEM ? "stack" : "tag", entry.value());
                encoded.addProperty("count", entry.requiredCount());
                encodedEntries.add(encoded);
            }
            json.add("entries", encodedEntries);
            // Keeping the target count in the self-contained matcher makes raw JSON portable;
            // required_entries remains the editable top-level authority when present.
            json.addProperty("required", requiredEntries);
            return json.toString();
        }
    }

    public record Removal(int slot, int count) {
        public Removal {
            if (slot < 0 || count < 1) throw new IllegalArgumentException("Invalid inventory removal");
        }
    }

    /** Runtime result for one authored entry; entryIndex is the stable submission selector. */
    public record Candidate(int entryIndex, ItemStack stack, int present, int required, boolean selected,
                            boolean exactComponents, List<Removal> removals) {
        public Candidate {
            stack = stack.copyWithCount(1);
            present = Math.max(0, present);
            required = Math.max(1, required);
            removals = List.copyOf(removals);
        }

        public boolean satisfied() { return present >= required; }
    }

    public record MatchPlan(List<Candidate> candidates, int requiredEntries, int satisfiedEntries,
                            boolean explicitSelection, boolean selectionValid) {
        public MatchPlan {
            candidates = List.copyOf(candidates);
            requiredEntries = Math.max(1, requiredEntries);
            satisfiedEntries = Math.max(0, satisfiedEntries);
        }

        public boolean satisfied() { return satisfiedEntries >= requiredEntries; }

        public ItemStack representative() {
            return candidates.stream().filter(Candidate::selected).findFirst()
                    .or(() -> candidates.stream().filter(Candidate::satisfied).findFirst())
                    .or(() -> candidates.stream().findFirst())
                    .map(candidate -> candidate.stack().copyWithCount(1)).orElse(ItemStack.EMPTY);
        }
    }

    /** Parses both the canonical version-2 entries and the two O.10 legacy matcher shapes. */
    public static DataResult<Spec> parse(String raw) {
        return parse(raw, 1, null);
    }

    /** Converts a complete schema-1 config map, including the old single-item representation. */
    public static DataResult<Spec> parseConfig(Map<String, String> config) {
        try {
            int legacyCount = config.containsKey("count") ? strictPositiveInt(config.get("count"), "count") : 1;
            Integer requiredOverride = config.containsKey("required_entries")
                    ? strictPositiveInt(config.get("required_entries"), "required_entries") : null;
            String matcher = config.getOrDefault("matcher", "");
            if (!matcher.isBlank()) return parse(matcher, legacyCount, requiredOverride);
            String item = config.getOrDefault("item", "");
            if (item.isBlank()) return DataResult.error(() -> "Item objective requires at least one item entry");
            return DataResult.success(new Spec(List.of(Entry.item(item, legacyCount)), 1));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** Produces the lossless editor view while retaining unrelated extension keys. */
    public static Map<String, String> canonicalEditorConfig(Map<String, String> config) {
        Spec spec = parseConfig(config).result().orElse(null);
        if (spec == null) return Map.copyOf(config);
        Map<String, String> canonical = new java.util.LinkedHashMap<>(config);
        canonical.remove("item");
        canonical.remove("count");
        canonical.remove("consume");
        canonical.put("matcher", spec.encode());
        canonical.put("required_entries", Integer.toString(spec.requiredEntries()));
        canonical.put("consume_items", Boolean.toString(booleanValue(config, "consume_items", "consume")));
        return Map.copyOf(canonical);
    }

    /** Registry-normalizes every child entry and writes the target-level required count into the matcher. */
    public static DataResult<Spec> normalizeConfig(net.minecraft.core.HolderLookup.Provider registries, Map<String, String> config) {
        return parseConfig(config).flatMap(spec -> normalize(registries, spec));
    }

    public static DataResult<Spec> normalize(net.minecraft.core.HolderLookup.Provider registries, String raw) {
        return parse(raw).flatMap(spec -> normalize(registries, spec));
    }

    public static DataResult<Spec> normalize(net.minecraft.core.HolderLookup.Provider registries, Spec spec) {
        try {
            List<Entry> normalized = new ArrayList<>();
            Set<String> unique = new LinkedHashSet<>();
            for (Entry entry : spec.entries()) {
                Entry value;
                if (entry.kind() == EntryKind.ITEM) {
                    ItemStack stack = parseStack(registries, entry.value());
                    if (stack.isEmpty()) return DataResult.error(() -> "Item objective contains an unknown item");
                    value = Entry.item(stack.copyWithCount(1).save(registries).toString(), entry.requiredCount());
                } else {
                    ResourceLocation tagId = ResourceLocation.tryParse(entry.value());
                    if (tagId == null || registries.lookupOrThrow(Registries.ITEM)
                            .get(TagKey.create(Registries.ITEM, tagId)).filter(tag -> tag.size() > 0).isEmpty()) {
                        return DataResult.error(() -> "Item tag is missing or empty: " + entry.value());
                    }
                    value = Entry.tag(tagId, entry.requiredCount());
                }
                String identity = entryIdentity(value);
                if (!unique.add(identity)) return DataResult.error(() -> "Item objective contains duplicate entries");
                normalized.add(value);
            }
            return DataResult.success(new Spec(normalized, spec.requiredEntries()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** Computes a plan, optionally honoring the player's explicit inventory slots. */
    public static MatchPlan plan(RegistryAccess registries, List<ItemStack> inventory, Spec spec,
                                 List<Integer> selectedSlots) {
        if (spec.entries().size() == 1 && spec.entries().getFirst().kind() == EntryKind.TAG) {
            return tagPlan(inventory, spec.entries().getFirst(), selectedSlots);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < spec.entries().size(); index++) {
            Entry entry = spec.entries().get(index);
            ItemStack expected = parseStack(registries, entry.value());
            int present = expected.isEmpty() ? 0 : inventory.stream()
                    .filter(stack -> stack.is(expected.getItem()))
                    .mapToInt(ItemStack::getCount).sum();
            // Authored ItemStacks provide identity and presentation; players choose the exact
            // component-bearing stacks to sacrifice later through inventory slot selection.
            candidates.add(new Candidate(index, expected, present, entry.requiredCount(), false, false, List.of()));
        }
        return applySelection(inventory, candidates, spec.requiredEntries(), selectedSlots);
    }

    public static MatchPlan plan(RegistryAccess registries, List<ItemStack> inventory, Spec spec) {
        return plan(registries, inventory, spec, List.of());
    }

    /** Applies a validated immutable plan; the enclosing task transaction owns rollback. */
    public static boolean consume(List<ItemStack> inventory, MatchPlan plan) {
        if (!plan.satisfied() || !plan.selectionValid()) return false;
        for (Candidate candidate : plan.candidates()) {
            if (!candidate.selected()) continue;
            int remaining = candidate.required();
            for (Removal removal : candidate.removals()) {
                if (removal.slot() >= inventory.size()) return false;
                ItemStack stack = inventory.get(removal.slot());
                if (!matches(stack, candidate) || stack.getCount() < removal.count()) return false;
                stack.shrink(removal.count());
                remaining -= removal.count();
            }
            if (remaining != 0) return false;
        }
        return true;
    }

    /** Representative stacks are editor/view data only; tags expand without entering persistence. */
    public static List<ItemStack> displayedCandidates(RegistryAccess registries, Spec spec) {
        if (spec.entries().size() == 1 && spec.entries().getFirst().kind() == EntryKind.TAG) {
            return tagItems(ResourceLocation.parse(spec.entries().getFirst().value()));
        }
        return spec.entries().stream().map(entry -> parseStack(registries, entry.value()))
                .filter(stack -> !stack.isEmpty()).map(stack -> stack.copyWithCount(1)).toList();
    }

    /** Client hint only; the server repeats this test against the live stack in the selected slot. */
    public static boolean accepts(RegistryAccess registries, Spec spec, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return spec.entries().stream().anyMatch(entry -> accepts(registries, entry, stack));
    }

    private static boolean accepts(RegistryAccess registries, Entry entry, ItemStack stack) {
        if (entry.kind() == EntryKind.TAG) {
            return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.value())));
        }
        ItemStack expected = parseStack(registries, entry.value());
        return !expected.isEmpty() && stack.is(expected.getItem());
    }

    private static DataResult<Spec> parse(String raw, int legacyCount, Integer requiredOverride) {
        try {
            JsonObject json = JsonParser.parseString(raw == null ? "" : raw).getAsJsonObject();
            if (json.has("entries")) return parseCanonical(json, requiredOverride);
            String mode = requiredString(json, "mode");
            if ("tag".equals(mode)) {
                ResourceLocation tag = ResourceLocation.tryParse(requiredString(json, "tag"));
                if (tag == null) throw new IllegalArgumentException("Item matcher tag must be namespaced");
                int count = json.has("count")
                        ? strictPositiveInt(json.get("count").getAsString(), "count") : legacyCount;
                return DataResult.success(new Spec(List.of(Entry.tag(tag, count)), 1));
            }
            if (!"list".equals(mode) || !json.has("items") || !json.get("items").isJsonArray()) {
                throw new IllegalArgumentException("Unknown item matcher mode: " + mode);
            }
            JsonArray items = json.getAsJsonArray("items");
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : items) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Legacy item candidates must be ItemStack SNBT strings");
                }
                entries.add(Entry.item(element.getAsString(), legacyCount));
            }
            int required = requiredOverride != null ? requiredOverride
                    : json.has("required") ? json.get("required").getAsInt() : 1;
            return DataResult.success(new Spec(entries, required));
        } catch (Exception exception) {
            return DataResult.error(() -> "Invalid item matcher: " + conciseMessage(exception));
        }
    }

    private static DataResult<Spec> parseCanonical(JsonObject json, Integer requiredOverride) {
        try {
            JsonArray entriesJson = json.getAsJsonArray("entries");
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : entriesJson) {
                JsonObject entry = element.getAsJsonObject();
                String kind = requiredString(entry, "kind");
                int count = entry.has("count") ? entry.get("count").getAsInt() : 1;
                if ("item".equals(kind)) entries.add(Entry.item(requiredString(entry, "stack"), count));
                else if ("tag".equals(kind)) {
                    ResourceLocation tag = ResourceLocation.tryParse(requiredString(entry, "tag"));
                    if (tag == null) throw new IllegalArgumentException("Item entry tag must be namespaced");
                    entries.add(Entry.tag(tag, count));
                } else throw new IllegalArgumentException("Unknown item entry kind: " + kind);
            }
            int required = requiredOverride != null ? requiredOverride
                    : json.has("required") ? json.get("required").getAsInt() : 1;
            return DataResult.success(new Spec(entries, required));
        } catch (Exception exception) {
            return DataResult.error(() -> "Invalid item matcher: " + conciseMessage(exception));
        }
    }

    private static MatchPlan tagPlan(List<ItemStack> inventory, Entry entry, List<Integer> selectedSlots) {
        ResourceLocation tagId = ResourceLocation.parse(entry.value());
        List<ItemStack> members = tagItems(tagId);
        ItemStack representative = members.isEmpty() ? ItemStack.EMPTY : members.getFirst();
        int present = 0;
        for (ItemStack inventoryStack : inventory) {
            if (inventoryStack.isEmpty() || members.stream().noneMatch(member -> inventoryStack.is(member.getItem()))) continue;
            int count = countItem(inventory, inventoryStack.getItem());
            if (count > present) {
                present = count;
                representative = inventoryStack.copyWithCount(1);
            }
            if (count >= entry.requiredCount()) break;
        }
        boolean explicit = selectedSlots != null && !selectedSlots.isEmpty();
        List<Removal> selectedRemovals = List.of();
        boolean valid = true;
        if (explicit) {
            int firstSlot = selectedSlots.getFirst();
            if (firstSlot < 0 || firstSlot >= inventory.size()) valid = false;
            ItemStack selectedStack = valid ? inventory.get(firstSlot) : ItemStack.EMPTY;
            if (selectedStack.isEmpty() || members.stream().noneMatch(member -> selectedStack.is(member.getItem()))) {
                valid = false;
            } else {
                representative = selectedStack.copyWithCount(1);
                selectedRemovals = removalsFromSlots(inventory, representative, false,
                        entry.requiredCount(), selectedSlots);
                valid = !selectedRemovals.isEmpty() && usesEverySlot(selectedRemovals, selectedSlots);
            }
        }
        boolean selected = present >= entry.requiredCount() && valid;
        Candidate candidate = new Candidate(0, representative, present, entry.requiredCount(), selected, false,
                selected ? explicit ? selectedRemovals
                        : removals(inventory, representative, false, entry.requiredCount()) : List.of());
        return new MatchPlan(List.of(candidate), 1, candidate.satisfied() ? 1 : 0, explicit,
                valid && candidate.satisfied());
    }

    private static MatchPlan applySelection(List<ItemStack> inventory, List<Candidate> base, int required,
                                            List<Integer> selectedSlots) {
        boolean explicit = selectedSlots != null && !selectedSlots.isEmpty();
        if (explicit) return applySlotSelection(inventory, base, required, selectedSlots);
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        base.stream().filter(Candidate::satisfied).limit(required).map(Candidate::entryIndex).forEach(selected::add);
        boolean indicesValid = selected.size() == required && selected.stream()
                .allMatch(index -> index >= 0 && index < base.size() && base.get(index).satisfied());
        List<Candidate> planned = new ArrayList<>();
        for (Candidate candidate : base) {
            boolean chosen = indicesValid && selected.contains(candidate.entryIndex());
            planned.add(new Candidate(candidate.entryIndex(), candidate.stack(), candidate.present(), candidate.required(),
                    chosen, candidate.exactComponents(), chosen
                    ? removals(inventory, candidate.stack(), candidate.exactComponents(), candidate.required()) : List.of()));
        }
        int satisfied = (int) base.stream().filter(Candidate::satisfied).count();
        return new MatchPlan(planned, required, satisfied, explicit, indicesValid);
    }

    /** Builds removals exclusively from the slots named by the player; unused extra slots invalidate the intent. */
    private static MatchPlan applySlotSelection(List<ItemStack> inventory, List<Candidate> base, int required,
                                                List<Integer> selectedSlots) {
        List<Candidate> planned = new ArrayList<>();
        Set<Integer> usedSlots = new LinkedHashSet<>();
        int selectedEntries = 0;
        for (Candidate candidate : base) {
            List<Removal> candidateRemovals = removalsFromSlots(inventory, candidate.stack(),
                    candidate.exactComponents(), candidate.required(), selectedSlots);
            boolean chosen = !candidateRemovals.isEmpty();
            if (chosen) {
                selectedEntries++;
                candidateRemovals.forEach(removal -> usedSlots.add(removal.slot()));
            }
            planned.add(new Candidate(candidate.entryIndex(), candidate.stack(), candidate.present(),
                    candidate.required(), chosen, candidate.exactComponents(), candidateRemovals));
        }
        boolean valid = selectedEntries == required && usedSlots.size() == selectedSlots.size()
                && selectedSlots.stream().allMatch(slot -> slot >= 0 && slot < inventory.size());
        int satisfied = (int) base.stream().filter(Candidate::satisfied).count();
        return new MatchPlan(planned, required, satisfied, true, valid);
    }

    private static boolean matches(ItemStack stack, Candidate candidate) {
        if (stack.isEmpty()) return false;
        return candidate.exactComponents()
                ? ItemStack.isSameItemSameComponents(stack, candidate.stack())
                : stack.is(candidate.stack().getItem());
    }

    private static List<Removal> removals(List<ItemStack> inventory, ItemStack expected,
                                          boolean exactComponents, int required) {
        List<Removal> result = new ArrayList<>();
        int remaining = required;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.get(slot);
            boolean matches = exactComponents
                    ? ItemStack.isSameItemSameComponents(stack, expected)
                    : stack.is(expected.getItem());
            if (!stack.isEmpty() && matches) {
                int count = Math.min(remaining, stack.getCount());
                result.add(new Removal(slot, count));
                remaining -= count;
            }
        }
        return remaining == 0 ? List.copyOf(result) : List.of();
    }

    private static List<Removal> removalsFromSlots(List<ItemStack> inventory, ItemStack expected,
                                                   boolean exactComponents, int required,
                                                   List<Integer> selectedSlots) {
        List<Removal> result = new ArrayList<>();
        int remaining = required;
        for (int slot : selectedSlots) {
            if (remaining <= 0 || slot < 0 || slot >= inventory.size()) break;
            ItemStack stack = inventory.get(slot);
            boolean matches = exactComponents
                    ? ItemStack.isSameItemSameComponents(stack, expected)
                    : stack.is(expected.getItem());
            if (!stack.isEmpty() && matches) {
                int count = Math.min(remaining, stack.getCount());
                result.add(new Removal(slot, count));
                remaining -= count;
            }
        }
        return remaining == 0 ? List.copyOf(result) : List.of();
    }

    private static boolean usesEverySlot(List<Removal> removals, List<Integer> selectedSlots) {
        Set<Integer> used = new LinkedHashSet<>();
        removals.forEach(removal -> used.add(removal.slot()));
        return used.size() == selectedSlots.size();
    }

    private static int countItem(List<ItemStack> inventory, Item item) {
        long total = inventory.stream().filter(stack -> stack.is(item)).mapToLong(ItemStack::getCount).sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static List<ItemStack> tagItems(ResourceLocation tagId) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
        Optional<? extends Iterable<Holder<Item>>> holders = BuiltInRegistries.ITEM.getTag(key)
                .map(set -> (Iterable<Holder<Item>>) set);
        if (holders.isEmpty()) return List.of();
        List<ItemStack> stacks = new ArrayList<>();
        holders.get().forEach(holder -> stacks.add(holder.value().getDefaultInstance()));
        return List.copyOf(stacks);
    }

    private static ItemStack parseStack(net.minecraft.core.HolderLookup.Provider registries, String snbt) {
        try {
            return ItemStack.parseOptional(registries, TagParser.parseTag(snbt));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String canonicalSnbt(String snbt) {
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id == null) throw new IllegalArgumentException("ItemStack requires a valid id");
            tag.putInt("count", 1);
            return tag.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid ItemStack SNBT: " + conciseMessage(exception));
        }
    }

    private static String entryIdentity(Entry entry) {
        if (entry.kind() == EntryKind.TAG) return "TAG\u0000" + entry.value();
        try {
            CompoundTag tag = TagParser.parseTag(entry.value());
            return "ITEM\u0000" + tag.getString("id");
        } catch (Exception exception) {
            return "ITEM\u0000" + entry.value();
        }
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Item matcher requires field " + key);
        }
        return json.get(key).getAsString();
    }

    private static int strictPositiveInt(String raw, String field) {
        try {
            long parsed = Long.parseLong(raw == null ? "" : raw.replaceAll("[bBsSlL]$", ""));
            if (parsed < 1 || parsed > Integer.MAX_VALUE) throw new NumberFormatException();
            return (int) parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
    }

    private static boolean booleanValue(Map<String, String> config, String key, String legacyKey) {
        String value = config.getOrDefault(key, "");
        if (value.isBlank()) value = config.getOrDefault(legacyKey, "false");
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    private static String conciseMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}

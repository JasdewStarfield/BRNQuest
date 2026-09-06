package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Immutable creation choices for one rendered type-picker frame. */
final class QuestTypePickerModel {
    enum Route { DIRECT, PROPERTY_FORM, ITEM_SELECTOR, CHOICE_SELECTOR }

    record Entry(ResourceLocation typeId, boolean builtIn, Route route) {}

    record Frame(QuestScreenFrameIdentity identity, QuestTypedEntryKind kind, List<Entry> entries) {
        Frame {
            entries = List.copyOf(entries);
        }

        Optional<Entry> select(QuestScreenFrameIdentity current, QuestTypedEntryKind currentKind,
                               ResourceLocation typeId) {
            if (!identity.equals(current) || kind != currentKind) return Optional.empty();
            return entries.stream().filter(entry -> entry.typeId().equals(typeId)).findFirst();
        }
    }

    private QuestTypePickerModel() {}

    static Frame frame(QuestScreenFrameIdentity identity, QuestTypedEntryKind kind) {
        List<Entry> entries = creatableTypeCandidates(kind.registeredTypes(), kind::addable, kind.hiddenLegacyAlias())
                .stream().map(typeId -> new Entry(typeId, kind.builtIns().contains(typeId), route(kind, typeId)))
                .toList();
        return new Frame(identity, kind, entries);
    }

    /** Existing unknown data stays preserved, while creation lists only advertise usable registrations. */
    static List<ResourceLocation> creatableTypeCandidates(Collection<ResourceLocation> registeredTypes,
                                                          Predicate<ResourceLocation> addable,
                                                          ResourceLocation hiddenLegacyAlias) {
        SortedSet<ResourceLocation> ids = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        ids.addAll(registeredTypes);
        ids.removeIf(type -> !addable.test(type) || type.equals(hiddenLegacyAlias));
        return List.copyOf(ids);
    }

    static Route route(QuestTypedEntryKind kind, ResourceLocation typeId) {
        if (kind.choiceBacked(typeId)) return Route.CHOICE_SELECTOR;
        if (kind.itemBacked(typeId)) return Route.ITEM_SELECTOR;
        return kind.builtIns().contains(typeId) ? Route.DIRECT : Route.PROPERTY_FORM;
    }
}

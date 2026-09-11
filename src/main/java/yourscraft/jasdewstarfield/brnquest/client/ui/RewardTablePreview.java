package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Immutable configuration tree; stable paths belong to preview rows, never to network reward identities. */
record RewardTablePreview(String path, String mode, int rolls, boolean replacement,
                          BigDecimal emptyWeight, List<Entry> entries) {
    record Entry(String path, RewardView reward, BigDecimal weight, boolean guaranteed,
                 Optional<RewardTablePreview> child) {}

    RewardTablePreview { entries = List.copyOf(entries); }

    /** Rebuild from the supplied snapshot/draft, without a global cache that can outlive edits or reloads. */
    static RewardTablePreview parse(RewardView root) {
        return project(root, RewardTableTree.parse(root.config().get("table")), "root");
    }

    private static RewardTablePreview project(RewardView root, RewardTableTree tree, String path) {
        List<Entry> entries = new ArrayList<>();
        for (var entry : tree.entries()) {
            String childPath = path + "/" + entry.get("entry_id").getAsString();
            var view = new RewardView(root.bookId(), root.id(), ResourceLocation.parse(entry.get("type").getAsString()),
                    RewardTableTree.config(entry), "manual", false);
            Optional<RewardTablePreview> child = entry.has("table")
                    ? Optional.of(project(root, RewardTableTree.parse(entry.get("table").toString()), childPath))
                    : Optional.empty();
            entries.add(new Entry(childPath, view, RewardTableTree.weight(entry), RewardTableTree.always(entry), child));
        }
        return new RewardTablePreview(path, tree.mode(), tree.rolls(), tree.replacement(), tree.emptyWeight(), entries);
    }

    /** Compatibility adapter for existing text browsers; resolve labels afresh in the current client language. */
    List<Component> lines(Function<RewardView, Component> labels) {
        List<Component> lines = new ArrayList<>();
        appendLines(labels, 0, lines);
        return List.copyOf(lines);
    }

    private void appendLines(Function<RewardView, Component> labels, int depth, List<Component> lines) {
        String indent = "  ".repeat(depth);
        lines.add(Component.literal(indent).append(Component.translatable("screen.brnquest.reward_table.mode." + mode)));
        if (mode.equals("random")) lines.add(Component.literal(indent).append(Component.translatable(
                "screen.brnquest.reward_table.preview", rolls, Component.translatable("screen.brnquest.reward_table."
                        + (replacement ? "with_replacement" : "without_replacement")), emptyWeight.toPlainString())));
        for (var entry : entries) {
            var label = Component.literal(indent + "  ").append(labels.apply(entry.reward()).copy());
            if (mode.equals("random")) label.append(" · ").append(Component.translatable(entry.guaranteed()
                    ? "screen.brnquest.reward_table.guaranteed" : "screen.brnquest.reward_table.weight_summary",
                    entry.weight().toPlainString()));
            lines.add(label);
            entry.child().ifPresent(child -> child.appendLines(labels, depth + 1, lines));
        }
    }
}

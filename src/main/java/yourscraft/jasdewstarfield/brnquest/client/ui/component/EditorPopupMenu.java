package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared clamped popup menu with one optional submenu level and deterministic hit testing. */
public final class EditorPopupMenu {
    public static final int ROW_HEIGHT = 18;

    /** Opaque action IDs are interpreted by the calling screen; the component owns presentation only. */
    public record Entry(String action, Component label, boolean dangerous, boolean enabled, List<Entry> children) {
        public Entry {
            if (label == null) throw new IllegalArgumentException("Menu label is required");
            action = action == null ? "" : action;
            children = children == null ? List.of() : List.copyOf(children);
            if (action.isBlank() == children.isEmpty()) {
                throw new IllegalArgumentException("A menu entry must be exactly one action or submenu");
            }
            if (children.stream().anyMatch(child -> !child.children().isEmpty())) {
                throw new IllegalArgumentException("Only one submenu level is supported");
            }
        }

        public boolean submenu() {
            return !children.isEmpty();
        }
    }

    /** Fluent constructor used by different editor surfaces without duplicating list assembly. */
    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        public Builder action(String action, Component label, boolean dangerous) {
            return action(action, label, dangerous, true);
        }

        public Builder action(String action, Component label, boolean dangerous, boolean enabled) {
            entries.add(new Entry(action, label, dangerous, enabled, List.of()));
            return this;
        }

        public Builder submenu(Component label, Consumer<Builder> children) {
            Builder childBuilder = new Builder();
            children.accept(childBuilder);
            entries.add(new Entry("", label, false, true, childBuilder.build()));
            return this;
        }

        public List<Entry> build() {
            return List.copyOf(entries);
        }
    }

    /** Root and child rectangles are calculated together so rendering and input cannot drift apart. */
    public record CascadeLayout(UiRect root, UiRect submenu, int submenuIndex) {
        public boolean contains(double mouseX, double mouseY) {
            return root.contains(mouseX, mouseY)
                    || submenu.width() > 0 && submenu.height() > 0 && submenu.contains(mouseX, mouseY);
        }
    }

    private EditorPopupMenu() {}

    public static List<Entry> menu(Consumer<Builder> entries) {
        Builder builder = new Builder();
        entries.accept(builder);
        return builder.build();
    }

    public static UiRect layout(int anchorX, int anchorY, int screenWidth, int contentTop, int contentBottom,
                                int menuWidth, int rowCount) {
        int menuHeight = Math.max(ROW_HEIGHT, rowCount * ROW_HEIGHT);
        int left = Math.max(4, Math.min(anchorX, screenWidth - menuWidth - 4));
        int top = Math.max(contentTop, Math.min(anchorY, contentBottom - menuHeight));
        return new UiRect(left, top, left + menuWidth, top + menuHeight);
    }

    public static CascadeLayout cascadeLayout(UiRect root, List<Entry> entries, int submenuIndex,
                                              int screenWidth, int contentTop, int contentBottom, int menuWidth) {
        if (!validSubmenu(entries, submenuIndex)) {
            return new CascadeLayout(root, new UiRect(root.right(), root.top(), root.right(), root.top()), -1);
        }
        int rows = entries.get(submenuIndex).children().size();
        int childHeight = Math.max(ROW_HEIGHT, rows * ROW_HEIGHT);
        int preferredLeft = root.right() - 1;
        int left = preferredLeft + menuWidth <= screenWidth - 4
                ? preferredLeft : Math.max(4, root.left() - menuWidth + 1);
        int preferredTop = root.top() + submenuIndex * ROW_HEIGHT;
        int top = Math.max(contentTop, Math.min(preferredTop, contentBottom - childHeight));
        return new CascadeLayout(root, new UiRect(left, top, left + menuWidth, top + childHeight), submenuIndex);
    }

    /** Hovering a submenu row opens it; moving into its child panel keeps it open. */
    public static int resolveSubmenu(UiRect root, List<Entry> entries, CascadeLayout current,
                                     double mouseX, double mouseY) {
        int rootRow = rowAt(root, entries.size(), mouseX, mouseY);
        if (rootRow >= 0) return entries.get(rootRow).submenu() ? rootRow : -1;
        if (current != null && validSubmenu(entries, current.submenuIndex())
                && current.submenu().contains(mouseX, mouseY)) {
            return current.submenuIndex();
        }
        return current == null ? -1 : current.submenuIndex();
    }

    public static void render(GuiGraphics graphics, Font font, CascadeLayout layout,
                              List<Entry> entries, int mouseX, int mouseY) {
        renderLevel(graphics, font, layout.root(), entries, mouseX, mouseY, true, layout.submenuIndex());
        if (validSubmenu(entries, layout.submenuIndex())) {
            renderLevel(graphics, font, layout.submenu(), entries.get(layout.submenuIndex()).children(),
                    mouseX, mouseY, false, -1);
        }
    }

    private static void renderLevel(GuiGraphics graphics, Font font, UiRect bounds, List<Entry> entries,
                                    int mouseX, int mouseY, boolean showSubmenuArrow, int expandedIndex) {
        GraystoneSurface.raised(graphics, bounds, GraystonePalette.PANEL, true);
        for (int index = 0; index < entries.size(); index++) {
            int top = bounds.top() + index * ROW_HEIGHT;
            Entry entry = entries.get(index);
            UiRect row = new UiRect(bounds.left() + 1, top + 1, bounds.right() - 1, top + ROW_HEIGHT - 1);
            graphics.fill(row.left(), row.top(), row.right(), row.bottom(),
                    row.contains(mouseX, mouseY) ? GraystonePalette.HOVER : GraystonePalette.ROW);
            EditorIcon icon = QuestActionIcons.action(entry.action());
            int iconSpace = icon == null ? 0 : 14;
            int color = !entry.enabled() ? GraystonePalette.DISABLED : entry.dangerous() ? 0xFFFF9B9B : 0xFFFFFFFF;
            if (icon != null) icon.render(graphics, font,
                    new UiRect(bounds.left() + 5, top + 4, bounds.left() + 15, top + 14), color);
            // Reserve the native sprite and its gap independently of the active font.
            int reserved = showSubmenuArrow && entry.submenu() ? 20 : 8;
            String label = font.plainSubstrByWidth(entry.label().getString(),
                    Math.max(1, row.width() - reserved - iconSpace));
            graphics.drawString(font, label, bounds.left() + 6 + iconSpace, top + 5,
                    !entry.enabled() ? GraystonePalette.DISABLED : entry.dangerous() ? 0xFFFF9B9B : 0xFFFFFFFF, false);
            if (showSubmenuArrow && entry.submenu()) {
                QuestActionIcons.named(expandedIndex == index ? "fold" : "unfold").render(graphics, font,
                        new UiRect(bounds.right() - 15, top + 4, bounds.right() - 5, top + 14), GraystonePalette.SECONDARY);
            }
        }
    }

    public static String actionAt(CascadeLayout layout, List<Entry> entries, double mouseX, double mouseY) {
        if (validSubmenu(entries, layout.submenuIndex())) {
            List<Entry> children = entries.get(layout.submenuIndex()).children();
            int childRow = rowAt(layout.submenu(), children.size(), mouseX, mouseY);
            if (childRow >= 0 && children.get(childRow).enabled()) return children.get(childRow).action();
        }
        int rootRow = rowAt(layout.root(), entries.size(), mouseX, mouseY);
        if (rootRow < 0 || entries.get(rootRow).submenu() || !entries.get(rootRow).enabled()) return "";
        return entries.get(rootRow).action();
    }

    public static int rowAt(UiRect bounds, int rowCount, double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return -1;
        int row = ((int) mouseY - bounds.top()) / ROW_HEIGHT;
        return row >= 0 && row < rowCount ? row : -1;
    }

    private static boolean validSubmenu(List<Entry> entries, int index) {
        return index >= 0 && index < entries.size() && entries.get(index).submenu();
    }
}

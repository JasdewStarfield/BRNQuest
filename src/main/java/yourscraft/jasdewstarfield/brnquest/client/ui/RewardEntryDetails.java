package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

import java.util.Optional;

/** Configuration-only reward projection shared by live cells, frozen choices and author drafts. */
record RewardEntryDetails(Component summary, ItemStack item, Optional<EditorIcon> decoration, String symbol) {
    RewardEntryDetails {
        summary = summary.copy();
        item = item.copy();
    }

    @Override public Component summary() { return summary.copy(); }
    @Override public ItemStack item() { return item.copy(); }

    /** Parse display data only; this entry point never inspects claim state or sends a request. */
    static RewardEntryDetails resolve(Minecraft minecraft, RewardView view) {
        var presentation = ClientRewardPresentationRegistry.get(view.typeId());
        ItemStack parsed = ItemStack.EMPTY;
        try {
            String snbt = presentation.itemSnbt(view);
            if (!snbt.isBlank() && minecraft != null && minecraft.level != null) {
                parsed = ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(snbt));
            }
        } catch (Exception ignored) {
            // Missing/oversized display data must leave a readable, selectable entry.
        }
        return resolve(minecraft, view, presentation, parsed);
    }

    /** The input is a raw parsed stack, never a previously multiplied display stack. */
    static RewardEntryDetails resolve(Minecraft minecraft, RewardView view,
                                      ClientRewardPresentation presentation, ItemStack parsed) {
        return fromDisplayed(minecraft, view, presentation, presentation.displayedItem(view, parsed.copy()));
    }

    /** Live reward cells already own the display copy; never apply their multiplier a second time. */
    static RewardEntryDetails fromDisplayed(Minecraft minecraft, RewardView view,
                                           ClientRewardPresentation presentation, ItemStack stack) {
        Component detail;
        if (!stack.isEmpty()) {
            detail = stack.getHoverName().copy().append(" × " + stack.getCount());
        } else if (view.typeId().equals(RewardTypes.XP) || view.typeId().equals(RewardTypes.XP_LEVELS)) {
            String key = view.typeId().equals(RewardTypes.XP) ? "xp" : "xp_levels";
            detail = presentation.typeName(view).copy().append(" × " + view.config().getOrDefault(key, "0"));
        } else {
            detail = presentation.title(new RewardPresentationContext(minecraft, view, false, false, stack.copy()));
            if (detail.equals(Component.translatable("screen.brnquest.reward.unknown"))) {
                detail = presentation.typeName(view);
            }
        }
        String title = view.config().getOrDefault("title", "");
        Component summary = title.isBlank() || detail.getString().equals(title) ? detail
                : Component.literal(title).append(" · ").append(detail);
        return new RewardEntryDetails(summary, stack, presentation.icon(view), presentation.symbol(view));
    }

    /** Decorative icons never acquire item lookup semantics even when a presentation also supplies a stack. */
    ItemStack lookupItem() { return decoration.isPresent() ? ItemStack.EMPTY : item(); }

    EditorIcon icon() {
        if (decoration.isPresent()) return decoration.orElseThrow();
        if (item.isEmpty()) return EditorIcon.glyph(Component.literal(symbol));
        ItemStack displayed = item();
        // EditorIcon.item intentionally draws count-one ghost icons; reward rows need the actual count.
        return new EditorIcon() {
            public int width(net.minecraft.client.gui.Font font) { return 16; }
            public void render(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                               yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect bounds, int color) {
                int x = bounds.centerX()-8, y = bounds.centerY()-8;
                graphics.renderItem(displayed, x, y);
                graphics.renderItemDecorations(font, displayed, x, y);
            }
        };
    }
}

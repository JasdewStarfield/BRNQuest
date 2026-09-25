package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

/**
 * Stateless objective/reward presentation. The returned geometry is the only source for
 * hit testing; callers decide what a click means and retain all server-authority checks.
 */
final class QuestDetailRows {
    private static final int ATTENTION_PING_SIZE = 10;
    private static final ResourceLocation CLAIMED_BADGE = ResourceLocation.parse("brnquest:quest/status/completed");
    private static final ResourceLocation SUBMITTABLE_PING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "brnquest", "textures/gui/submitable_ping.png");
    private static final ResourceLocation REWARD_PING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "brnquest", "textures/gui/reward_ping.png");
    record Result(int nextY, UiRect action, UiRect candidates, RecipeLookupTarget lookup, Component hint) {}

    static Result task(GuiGraphics graphics, Font font, TaskDefinition task, ClientTaskPresentation presentation,
                       TaskPresentationContext presentationContext, TaskDisplayState displayState,
                       int x, int y, int width, UiRect viewport, int mouseX, int mouseY, int pingOffset) {
        var taskView = presentationContext.task();
        ItemStack stack = presentationContext.displayedItem();
        boolean locallySatisfied = presentation.satisfied(presentationContext);
        graphics.fill(x, y, x + width, y + 24, taskRowBackground(displayState));
        var icon = presentation.icon(taskView);
        // Registered type art and truly unknown types use sprites; symbol-only addons keep their glyphs.
        if (icon.isEmpty() && stack.isEmpty()) {
            var typeIcon = ClientTaskPresentationRegistry.typeIcon(taskView.typeId());
            if (ClientTaskPresentationRegistry.hasTypeIcon(taskView.typeId())
                    || !ClientTaskPresentationRegistry.hasPresentation(taskView.typeId())) icon = java.util.Optional.of(typeIcon);
        }
        if (icon.isPresent()) icon.orElseThrow().render(graphics,font,new UiRect(x+3,y+4,x+19,y+20),0xFFFFFFFF);
        else if (!stack.isEmpty()) graphics.renderItem(stack, x + 3, y + 4);
        else graphics.drawCenteredString(font, presentation.symbol(taskView), x + 11, y + 8, 0xFFFFFFFF);
        if (displayState == TaskDisplayState.READY && !stack.isEmpty()) {
            renderAttentionPing(graphics, SUBMITTABLE_PING_TEXTURE, x + 17, y - 2, pingOffset);
        }

        UiRect visibleRow = visiblePart(new UiRect(x, y, x + width, y + 24), viewport);
        UiRect clickable = null;
        Component hoveredText = null;
        RecipeLookupTarget hoveredLookup = null;
        boolean candidateMenu = presentation.hasCandidateMenu(presentationContext);
        UiRect candidateBounds = candidateMenu
                ? new UiRect(x + 21, y + 4, x + 35, y + 20) : null;
        UiRect visibleCandidate = visiblePart(candidateBounds, viewport);
        if (candidateBounds != null) {
            graphics.fill(candidateBounds.left(), candidateBounds.top(), candidateBounds.right(),
                    candidateBounds.bottom(), visibleCandidate != null && visibleCandidate.containsExclusive(mouseX, mouseY)
                            ? 0xFF62664F : 0xFF484C3E);
            QuestActionIcons.named("detail").render(graphics, font, candidateBounds, 0xFFFFFFFF);

        }
        int textInset = candidateMenu ? 43 : 28;
        Component title = presentation.objectiveTitle(presentationContext);
        UiRect titleHintBounds = drawTaskObjectiveTitle(graphics, font, task, presentation, presentationContext,
                title, x + width - 4, y + 3, Math.max(1, width - textInset),
                taskTitleColor(displayState));
        Component progress = taskStateText(presentation, presentationContext, displayState, locallySatisfied);
        if (task.optional()) progress = progress.copy().append(" · ").append(Component.translatable("screen.brnquest.optional"));
        EditorTextRenderer.drawFittedStringRight(graphics, font, progress, x + width - 4, y + 13,
                Math.max(1, width - textInset), taskProgressColor(displayState), 0.75F);
        UiRect itemBounds = new UiRect(x + 3, y + 4, x + 19, y + 20);
        RecipeLookupTarget lookup = RecipeLookupTarget.clipped(icon.isPresent() ? ItemStack.EMPTY : stack, itemBounds, viewport).orElse(null);
        boolean itemHovered = lookup != null && lookup.contains(mouseX, mouseY);
        boolean interactive = displayState.actionable();
        if (interactive) clickable = visibleRow;
        boolean rowHovered = visibleRow != null && visibleRow.containsExclusive(mouseX, mouseY);
        UiRect visibleTitleHint = visiblePart(titleHintBounds, viewport);
        boolean titleHintHovered = visibleTitleHint != null && visibleTitleHint.containsExclusive(mouseX, mouseY);
        if (itemHovered) {
            // The ItemStack tooltip and JEI lookup share the exact rendered 16px icon bounds.
            hoveredLookup = lookup;
        } else if (titleHintHovered) {
            hoveredText = presentation.titleDecoration(presentationContext).map(ClientTaskPresentation.TitleDecoration::hint).orElse(title);
        } else if (visibleCandidate != null && visibleCandidate.containsExclusive(mouseX, mouseY)) {
            hoveredText = Component.translatable(presentation.resolvedOptions(taskView).isPresent()
                    ? "screen.brnquest.options.title" : "screen.brnquest.item_choice.view_candidates");
        } else if (rowHovered && font.width(title) * 0.75F > Math.max(1, width - textInset)) {
            // Truncation must never hide the full objective name behind an action-only hint.
            hoveredText = title.copy().append("\n").append(progress);
        } else if (interactive && rowHovered) {
            // The item and semantic qualifier keep their more specific help; the remaining row
            // communicates that the complete actionable row submits this objective.
            hoveredText = Component.translatable("screen.brnquest.task.click_to_submit");
        } else if (displayState == TaskDisplayState.HISTORICAL && rowHovered) {
            hoveredText = Component.translatable("screen.brnquest.task.historical_hint");
        } else if ((icon.isPresent() || stack.isEmpty()) && rowHovered) {
            hoveredText = presentation.interactionHint(presentationContext, interactive);
        }
        return new Result(y + 28, clickable, visibleCandidate, hoveredLookup, hoveredText);
    }

    static Result reward(GuiGraphics graphics, Font font, ClientRewardPresentation presentation,
                         RewardPresentationContext context, int x, int y, UiRect viewport,
                         int mouseX, int mouseY, int pingOffset) {
        var rewardView = context.reward();
        ItemStack stack = context.displayedItem();
        boolean claimed = context.claimed();
        boolean claimable = context.claimable();
        var icon = presentation.icon(rewardView);
        if (icon.isEmpty() && stack.isEmpty()) {
            var typeIcon = ClientRewardPresentationRegistry.typeIcon(rewardView.typeId());
            if (ClientRewardPresentationRegistry.hasTypeIcon(rewardView.typeId())
                    || !ClientRewardPresentationRegistry.hasPresentation(rewardView.typeId())) icon = java.util.Optional.of(typeIcon);
        }
        // Reward cells retain compact quantity overlays while sharing the graystone inset surface.
        graphics.fill(x, y, x + 24, y + 24, claimed ? 0xFF30372F : claimable ? 0xFF514D36 : 0xFF34382F);
        if (icon.isPresent()) icon.orElseThrow().render(graphics,font,new UiRect(x+4,y+4,x+20,y+20),0xFFFFFFFF);
        else if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 4, y + 4);
            // Match vanilla slot rendering so configured reward multipliers appear at bottom-right.
            graphics.renderItemDecorations(font, stack, x + 4, y + 4);
        }
        else graphics.drawCenteredString(font, presentation.symbol(rewardView), x + 12, y + 8, 0xFFFFFFFF);
        // Claim attention belongs to the reward state, including symbol-only extension rewards.
        if (claimable) {
            renderAttentionPing(graphics, REWARD_PING_TEXTURE, x + 18, y - 2, pingOffset);
        }
        // Keep the claimed marker above the icon; the bottom-right corner belongs to vanilla count text.
        if (claimed) renderClaimedRewardCheck(graphics, x + 17, y - 2);
        UiRect visibleCell = visiblePart(new UiRect(x, y, x + 24, y + 24), viewport);
        UiRect clickable = null;
        Component hoveredText = null;
        RecipeLookupTarget hoveredLookup = null;
        UiRect itemBounds = new UiRect(x + 4, y + 4, x + 20, y + 20);
        RecipeLookupTarget lookup = RecipeLookupTarget.clipped(icon.isPresent() ? ItemStack.EMPTY : stack, itemBounds, viewport).orElse(null);
        boolean itemHovered = lookup != null && lookup.contains(mouseX, mouseY);
        if (claimable) clickable = visibleCell;
        if (itemHovered) {
            // Item tooltip and recipe lookup now stop together at the icon's exclusive edges.
            hoveredLookup = lookup;
        } else if ((icon.isPresent() || stack.isEmpty()) && visibleCell != null && visibleCell.containsExclusive(mouseX, mouseY)) {
            // Real items keep only the native tooltip; do not expose a competing hint at the cell edge.
            var details = RewardEntryDetails.fromDisplayed(context.minecraft(), rewardView, presentation, stack);
            var hint = presentation.interactionHint(context);
            hoveredText = details.summary();
            if (hint.getString().contains(hoveredText.getString())) hoveredText = hint;
            else if (!hint.equals(presentation.title(context))) hoveredText = hoveredText.copy().append("\n").append(hint);
        }
        UiRect candidates = presentation.resolvedOptions(rewardView).isPresent()
                ? visiblePart(new UiRect(x+25,y+4,x+39,y+20),viewport) : null;
        if (candidates != null) {
            boolean hovered = candidates.containsExclusive(mouseX,mouseY);
            graphics.fill(candidates.left(),candidates.top(),candidates.right(),candidates.bottom(),hovered ? 0xFF62664F : 0xFF484C3E);
            // Center on the original button even when its visible hitbox is clipped by scrolling.
            QuestActionIcons.named("detail").render(graphics, font, new UiRect(x+25,y+4,x+39,y+20), 0xFFFFFFFF);
            if (hovered) { hoveredLookup = null; hoveredText = Component.translatable("screen.brnquest.options.title"); }
        }
        return new Result(y + 24, clickable, candidates, hoveredLookup, hoveredText);
    }

    /** Partially clipped controls remain usable only through the pixels the player can still see. */
    static UiRect visiblePart(UiRect bounds, UiRect viewport) {
        if (bounds == null || viewport == null) return null;
        UiRect visible = bounds.intersection(viewport);
        return visible.width() > 0 && visible.height() > 0 ? visible : null;
    }

    static void renderAttentionPing(GuiGraphics graphics, ResourceLocation texture, int x, int y, int offset) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.blit(texture, x, y + offset, 0.0F, 0.0F,
                ATTENTION_PING_SIZE, ATTENTION_PING_SIZE, ATTENTION_PING_SIZE, ATTENTION_PING_SIZE);
        graphics.pose().popPose();
    }

    private static UiRect drawTaskObjectiveTitle(GuiGraphics graphics, Font font, TaskDefinition task,
                                          ClientTaskPresentation presentation,
                                          TaskPresentationContext context, Component fallbackTitle,
                                          int right, int y, int maximumWidth, int color) {
        var decoration = presentation.titleDecoration(context).orElse(null);
        if (decoration == null) {
            EditorTextRenderer.drawFittedStringRight(graphics, font, fallbackTitle, right, y, maximumWidth, color, 0.75F);
            return null;
        }

        String configuredTitle = decoration.wholeTitle() ? decoration.subject().getString() : "";
        if (!configuredTitle.isBlank()) {
            Component customTitle = Component.literal(configuredTitle);
            float scale = EditorTextLayout.fittedScale(font.width(customTitle), maximumWidth, 0.75F);
            int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
            String visible = font.plainSubstrByWidth(configuredTitle, unscaledWidth);
            int left = right - Math.round(font.width(visible) * scale);
            graphics.pose().pushPose();
            graphics.pose().translate(left, y, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, visible, 0, 0, color, false);
            graphics.pose().popPose();
            return new UiRect(left, y, right, y + Math.max(1, Math.round(font.lineHeight * scale)));
        }

        Component qualifier = decoration.qualifier();
        String qualifierText = qualifier.getString();
        String subject = decoration.subject().getString();
        String fullText = qualifierText + " " + subject;
        float scale = EditorTextLayout.fittedScale(font.width(fullText), maximumWidth, 0.75F);
        int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
        String visible = font.plainSubstrByWidth(fullText, unscaledWidth);
        int left = right - Math.round(font.width(visible) * scale);
        int visibleQualifierLength = Math.min(qualifierText.length(), visible.length());

        var styledText = Component.empty().append(
                Component.literal(visible.substring(0, visibleQualifierLength))
                        .withStyle(ChatFormatting.UNDERLINE));
        if (visibleQualifierLength < visible.length()) {
            styledText.append(Component.literal(visible.substring(visibleQualifierLength)));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(left, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, styledText, 0, 0, color, false);
        graphics.pose().popPose();

        int qualifierWidth = Math.round(font.width(visible.substring(0, visibleQualifierLength)) * scale);
        int lineHeight = Math.max(1, Math.round(font.lineHeight * scale));
        return new UiRect(left, y, left + qualifierWidth, y + lineHeight);
    }

    private static Component taskStateText(ClientTaskPresentation presentation, TaskPresentationContext context,
                                    TaskDisplayState state, boolean locallySatisfied) {
        return switch (state) {
            case READY, UNMET -> presentation.progressText(context, locallySatisfied);
            case PENDING -> Component.translatable("screen.brnquest.task.awaiting_confirmation");
            case SUBMITTED -> Component.translatable("screen.brnquest.task.submitted");
            case HISTORICAL -> Component.translatable("screen.brnquest.task.historical");
        };
    }

    private static int taskRowBackground(TaskDisplayState state) {
        return switch (state) {
            case READY, PENDING -> 0xFF494536;
            case SUBMITTED -> 0xFF303F32;
            case UNMET, HISTORICAL -> 0xFF34382F;
        };
    }

    private static int taskTitleColor(TaskDisplayState state) {
        return switch (state) {
            case READY, PENDING -> 0xFFF2C96D;
            case SUBMITTED -> 0xFF8BE2A0;
            case HISTORICAL -> GraystonePalette.SECONDARY;
            case UNMET -> 0xFFFFFFFF;
        };
    }

    private static int taskProgressColor(TaskDisplayState state) {
        return switch (state) {
            case READY, PENDING -> 0xFFE6B55B;
            case SUBMITTED -> 0xFF72D88D;
            case UNMET, HISTORICAL -> GraystonePalette.SECONDARY;
        };
    }

    private static void renderClaimedRewardCheck(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        try {
            // Reuse the editable completion badge above native item depth, away from count overlays.
            graphics.pose().translate(0, 0, 300);
            graphics.blitSprite(CLAIMED_BADGE, x, y, 10, 10);
        } finally {
            graphics.pose().popPose();
        }
    }
}

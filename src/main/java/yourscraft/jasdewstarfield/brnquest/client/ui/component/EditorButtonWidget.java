package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared editor paint with vanilla widget focus, narration and keyboard activation. */
public final class EditorButtonWidget extends Button {
    private final EditorButton.Definition definition;
    private EditorIcon iconOverride;
    private EditorButton.Tone tone = EditorButton.Tone.NEUTRAL;

    public EditorButtonWidget(int x, int y, int width, int height, Component label, OnPress action) {
        this(x, y, width, height, EditorButton.Definition.text(label, null), action);
    }

    /** Sprite-backed widgets keep native keyboard activation, narration and tooltip handling. */
    public EditorButtonWidget(int x, int y, int width, int height, EditorButton.Definition definition, OnPress action) {
        super(x, y, width, height, definition.label(), action, DEFAULT_NARRATION);
        this.definition = definition;
        if (!definition.tooltip().isEmpty()) {
            Component tooltip = Component.empty();
            for (Component line : definition.tooltip()) {
                if (!tooltip.getString().isEmpty()) tooltip = tooltip.copy().append("\n");
                tooltip = tooltip.copy().append(line);
            }
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
        }
    }

    /** Updated choices keep the full current label available even when the compact button needs ellipsis. */
    @Override public void setMessage(Component message) {
        super.setMessage(message);
        if (definition == null) return;
        Component tooltip = message.copy();
        for (Component line : definition.tooltip()) {
            if (!line.equals(definition.label())) tooltip = tooltip.copy().append("\n").append(line);
        }
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    /** Native widget hosts can use the same primary/destructive tones as the action-group controls. */
    public void setTone(EditorButton.Tone tone) { this.tone = tone; }

    @Override public void onPress() {
        // The native Button input path already plays the click sound; do not play a second one here.
        EditorButtonFeedback.pulse(new UiRect(getX(), getY(), getX()+getWidth(), getY()+getHeight()));
        super.onPress();
    }

    /** Disclosure controls can reflect an open menu without replacing their native widget. */
    public void setIcon(EditorIcon icon) { iconOverride = icon; }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Only replace paint: the Screen still owns tab order and dispatches each activation once.
        EditorButton.render(graphics, Minecraft.getInstance().font,
                new UiRect(getX(), getY(), getX() + getWidth(), getY() + getHeight()),
                // setMessage still updates busy/retry labels without discarding the icon.
                new EditorButton.Definition(getMessage(), definition.tooltip(),
                        iconOverride == null ? definition.icon() : iconOverride, definition.contentMode()),
                new EditorButton.State(active, isHovered(), isFocused()), tone.palette());
    }
}

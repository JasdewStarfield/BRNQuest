package yourscraft.jasdewstarfield.brnquest.compat.jei;

import com.mojang.datafixers.util.Either;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.client.ui.EditorItemSelectorScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.EditorLocalizedQuestTextScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.ItemChoiceScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.ItemSubmissionScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.QuestScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupHint;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupSource;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TransientChildScreenParent;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.List;
import java.util.Optional;

/**
 * Optional JEI entry point. No core BRNQuest class references this package, so the JVM never
 * resolves JEI types when the mod is absent; JEI itself discovers this class when installed.
 */
@JeiPlugin
public final class BrnQuestJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, "editor");
    private IJeiRuntime runtime;

    public BrnQuestJeiPlugin() {
        // This class exists only in JEI's optional discovery path, so registering a client event
        // listener here cannot resolve JEI classes during a dependency-absent BRNQuest startup.
        NeoForge.EVENT_BUS.addListener(this::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(this::onTooltipGathering);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Registering a plain Screen makes JEI render its ingredient list beside the selector layer.
        registration.addGuiScreenHandler(EditorItemSelectorScreen.class, BrnQuestJeiPlugin::selectorProperties);
        registration.addGuiScreenHandler(ItemChoiceScreen.class, BrnQuestJeiPlugin::choiceProperties);
        registration.addGuiScreenHandler(ItemSubmissionScreen.class, BrnQuestJeiPlugin::submissionProperties);
        // Global clickable ingredients are queried only for JEI-managed screens. QuestScreen is
        // full-width, so registration activates recipe/use input without reserving overlay space.
        registration.addGuiScreenHandler(QuestScreen.class, BrnQuestJeiPlugin::questProperties);
        registration.addGuiScreenHandler(EditorLocalizedQuestTextScreen.class,
                BrnQuestJeiPlugin::localizedTextProperties);
        registration.addGhostIngredientHandler(EditorItemSelectorScreen.class, new ItemSelectorGhostHandler());
        registration.addGhostIngredientHandler(ItemChoiceScreen.class, new ItemChoiceGhostHandler());
        registration.addGlobalGuiHandler(new BrnQuestClickableItemHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // Read the live mappings on demand so in-game key rebinding is reflected immediately.
        RecipeLookupHint.install(() -> lookupHint(jeiRuntime));
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        RecipeLookupHint.clear();
    }

    private void onScreenOpening(ScreenEvent.Opening event) {
        Screen current = event.getCurrentScreen();
        if (runtime == null || !(current instanceof TransientChildScreenParent parent)
                || event.getNewScreen() == current) return;
        // JEI assigns its parent before setScreen fires Opening. Mark only that exact transition,
        // leaving ordinary inventory, disconnect, and explicit editor-close lifecycles untouched.
        if (runtime.getRecipesGui().getParentScreen().orElse(null) == current) {
            parent.prepareForTransientChildScreen();
        }
    }

    private void onTooltipGathering(RenderTooltipEvent.GatherComponents event) {
        if (runtime == null || event.getItemStack().isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (!(screen instanceof RecipeLookupSource source)) return;
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
                / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
                / minecraft.getWindow().getScreenHeight();
        source.recipeLookupTargetAt(mouseX, mouseY)
                .filter(target -> ItemStack.isSameItemSameComponents(target.stack(), event.getItemStack()))
                .flatMap(ignored -> lookupHint(runtime))
                .ifPresent(hint -> event.getTooltipElements().add(Either.left(hint)));
    }

    private static Optional<Component> lookupHint(IJeiRuntime runtime) {
        var recipe = runtime.getKeyMappings().getShowRecipe();
        var uses = runtime.getKeyMappings().getShowUses();
        if (recipe.isUnbound() && uses.isUnbound()) return Optional.empty();
        Component hint;
        if (recipe.isUnbound()) {
            hint = Component.translatable("screen.brnquest.jei.lookup_hint.uses",
                    uses.getTranslatedKeyMessage());
        } else if (uses.isUnbound()) {
            hint = Component.translatable("screen.brnquest.jei.lookup_hint.recipe",
                    recipe.getTranslatedKeyMessage());
        } else {
            hint = Component.translatable("screen.brnquest.jei.lookup_hint.both",
                    recipe.getTranslatedKeyMessage(), uses.getTranslatedKeyMessage());
        }
        return Optional.of(hint.copy().withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static IGuiProperties selectorProperties(EditorItemSelectorScreen screen) {
        if (!hasValidDimensions(screen)) return null;
        UiRect panel = screen.selectorLayout().panel();
        if (panel.width() <= 1 || panel.height() <= 1) return null;
        return new ScreenGuiProperties(EditorItemSelectorScreen.class, panel.left(), panel.top(), panel.width(),
                panel.height(), screen.width, screen.height);
    }

    @Nullable
    private static IGuiProperties choiceProperties(ItemChoiceScreen screen) {
        if (!hasValidDimensions(screen)) return null;
        UiRect panel = screen.panelBounds();
        if (panel.width() <= 1 || panel.height() <= 1) return null;
        return new ScreenGuiProperties(ItemChoiceScreen.class, panel.left(), panel.top(), panel.width(),
                panel.height(), screen.width, screen.height);
    }

    @Nullable
    private static IGuiProperties submissionProperties(ItemSubmissionScreen screen) {
        if (!hasValidDimensions(screen)) return null;
        UiRect panel = screen.selectorLayout().panel();
        if (panel.width() <= 1 || panel.height() <= 1) return null;
        return new ScreenGuiProperties(ItemSubmissionScreen.class, panel.left(), panel.top(), panel.width(),
                panel.height(), screen.width, screen.height);
    }

    @Nullable
    private static IGuiProperties questProperties(QuestScreen screen) {
        if (!hasValidDimensions(screen)) return null;
        return new ScreenGuiProperties(QuestScreen.class, 0, 0, screen.width, screen.height,
                screen.width, screen.height);
    }

    @Nullable
    private static IGuiProperties localizedTextProperties(EditorLocalizedQuestTextScreen screen) {
        if (!hasValidDimensions(screen)) return null;
        // Full-screen bounds activate recipe/use keys without placing JEI's ingredient list over the editor.
        return new ScreenGuiProperties(EditorLocalizedQuestTextScreen.class, 0, 0, screen.width, screen.height,
                screen.width, screen.height);
    }

    /** JEI may query a newly assigned Screen once before Minecraft calls its resize/init path. */
    private static boolean hasValidDimensions(Screen screen) {
        return screen.width > 1 && screen.height > 1;
    }

    /** Immutable JEI layout snapshot derived from the same geometry used for rendering and hit testing. */
    private record ScreenGuiProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
                                       int guiXSize, int guiYSize, int screenWidth,
                                       int screenHeight) implements IGuiProperties {}

    private static final class BrnQuestClickableItemHandler implements IGlobalGuiHandler {
        @Override
        public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
                IClickableIngredientFactory builder, double mouseX, double mouseY) {
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof RecipeLookupSource source)) return Optional.empty();
            return source.recipeLookupTargetAt(mouseX, mouseY).flatMap(target -> {
                UiRect area = target.bounds();
                // JEI wraps plugin-provided GUI ingredients with canClickToFocus=false. Its
                // recipe/use keys work, while ordinary mouse clicks remain owned by BRNQuest.
                return builder.createBuilder(target.stack())
                        .buildWithArea(area.left(), area.top(), area.width(), area.height())
                        .map(clickable -> (IClickableIngredient<?>) clickable);
            });
        }
    }

    private static final class ItemSelectorGhostHandler implements IGhostIngredientHandler<EditorItemSelectorScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(EditorItemSelectorScreen screen,
                                                   ITypedIngredient<I> ingredient, boolean doStart) {
            if (ingredient.getType() != VanillaTypes.ITEM_STACK || ingredient.getItemStack().orElse(ItemStack.EMPTY).isEmpty()) {
                return List.of();
            }
            UiRect target = screen.selectorLayout().targetSlot();
            return List.of(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(target.left(), target.top(), target.width(), target.height());
                }

                @Override
                public void accept(I dropped) {
                    if (dropped instanceof ItemStack stack) screen.acceptGhostItem(stack);
                }
            });
        }

        @Override
        public void onComplete() {
            // The target commits its count-one copy in accept; no source inventory cleanup is needed.
        }
    }

    private static final class ItemChoiceGhostHandler implements IGhostIngredientHandler<ItemChoiceScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(ItemChoiceScreen screen,
                                                   ITypedIngredient<I> ingredient, boolean doStart) {
            if (!screen.editing() || ingredient.getType() != VanillaTypes.ITEM_STACK
                    || ingredient.getItemStack().orElse(ItemStack.EMPTY).isEmpty()) {
                return List.of();
            }
            UiRect target = screen.ghostTargetBounds();
            return List.of(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(target.left(), target.top(), target.width(), target.height());
                }

                @Override
                public void accept(I dropped) {
                    if (dropped instanceof ItemStack stack) screen.acceptGhostItem(stack);
                }
            });
        }

        @Override
        public void onComplete() {
            // The Screen stores a count-one copy, so the JEI source remains untouched.
        }
    }
}

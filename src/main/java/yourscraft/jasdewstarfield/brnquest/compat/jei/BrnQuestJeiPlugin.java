package yourscraft.jasdewstarfield.brnquest.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.client.ui.EditorItemSelectorScreen;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.List;

/**
 * Optional JEI entry point. No core BRNQuest class references this package, so the JVM never
 * resolves JEI types when the mod is absent; JEI itself discovers this class when installed.
 */
@JeiPlugin
public final class BrnQuestJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, "editor");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Registering a plain Screen makes JEI render its ingredient list beside the selector layer.
        registration.addGuiScreenHandler(EditorItemSelectorScreen.class, BrnQuestJeiPlugin::properties);
        registration.addGhostIngredientHandler(EditorItemSelectorScreen.class, new ItemSelectorGhostHandler());
    }

    private static IGuiProperties properties(EditorItemSelectorScreen screen) {
        UiRect panel = screen.selectorLayout().panel();
        return new SelectorGuiProperties(EditorItemSelectorScreen.class, panel.left(), panel.top(), panel.width(),
                panel.height(), screen.width, screen.height);
    }

    /** Immutable JEI layout snapshot derived from the same geometry used for rendering and hit testing. */
    private record SelectorGuiProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
                                         int guiXSize, int guiYSize, int screenWidth,
                                         int screenHeight) implements IGuiProperties {}

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
}

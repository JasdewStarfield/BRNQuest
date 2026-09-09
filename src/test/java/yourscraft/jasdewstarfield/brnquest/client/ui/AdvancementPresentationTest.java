package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementPresentationTest {
    @Test void nativeDisplayIsCopiedAndMissingOrGroupedDisplaysUseFallback() {
        var original = new ItemStack(Items.DIAMOND,7);
        var icon = AdvancementPresentation.displayIcon("minecraft:story/root",id -> original);
        assertTrue(icon.is(Items.DIAMOND)); assertEquals(1,icon.getCount()); assertEquals(7,original.getCount());
        assertTrue(AdvancementPresentation.displayIcon("test:hidden",id -> ItemStack.EMPTY).is(Items.KNOWLEDGE_BOOK));
        assertTrue(AdvancementPresentation.displayIcon("#test:group",id -> { throw new AssertionError("Groups are not advancement IDs"); }).is(Items.KNOWLEDGE_BOOK));
    }
    @Test void nativeTitleKeepsTranslationComponentsAndFallsBackWhenUnavailable() {
        var title = Component.translatableWithFallback("test.advancement.title", "Localized title");
        assertEquals(title,AdvancementPresentation.displayTitle("test:one",id -> title));
        assertNotSame(title,AdvancementPresentation.displayTitle("test:one",id -> title));
        assertEquals("test:one",AdvancementPresentation.displayTitle("test:one",id -> null).getString());
        assertEquals("test:one",AdvancementPresentation.displayTitle("test:one",id -> Component.empty()).getString());
        assertEquals("test:one",AdvancementPresentation.displayTitle("test:one",id -> Component.translatable("test.missing.title")).getString());
        assertEquals("#test:group",AdvancementPresentation.displayTitle("#test:group",id -> { throw new AssertionError("No native group title"); }).getString());
    }
    @Test void presentationsDoNotExposeAnIngredientOrItemCandidate() {
        var book=ResourceLocation.parse("test:book"); var id=ResourceLocation.parse("test:entry");
        var type=ResourceLocation.parse("brnquest:advancement");
        var task=ApiViews.task(new TaskDefinition(book,id,type,Map.of("advancement","test:one"),false));
        var reward=ApiViews.reward(new RewardDefinition(book,id,type,task.config(),"manual",false));
        assertEquals("",new AdvancementPresentation.Task().itemSnbt(task));
        assertEquals("",new AdvancementPresentation.Reward().itemSnbt(reward));
        assertEquals(ClientTaskPresentation.NodeStyle.CUSTOM,new AdvancementPresentation.Task().nodeStyle(task));
    }
    @Test void rewardTooltipKeepsSelectionAndClaimStateWithoutMechanics() {
        var values=Map.of("title","Author title","advancement","test:one","criterion","my_criterion");
        var hint=AdvancementPresentation.rewardHint(values,true,false).getString();
        assertTrue(hint.contains("Author title")); assertTrue(hint.contains("test:one")); assertTrue(hint.contains("my_criterion"));
        assertFalse(hint.contains(Component.translatable("screen.brnquest.advancement.reward_hint").getString()));
        assertTrue(hint.contains(Component.translatable("screen.brnquest.reward.click_to_claim").getString()));
        assertTrue(AdvancementPresentation.rewardHint(values,false,true).getString()
                .contains(Component.translatable("screen.brnquest.reward.claimed").getString()));
    }
}

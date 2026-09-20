package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.builtin.client.AdvancementPresentation;

import org.junit.jupiter.api.Test;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementPresentationTest {
    @Test void groupTooltipListsTranslatedMembersSeparately() {
        var detail=Component.literal("Heading");
        AdvancementPresentation.appendMembers(detail,java.util.List.of("test:a","test:b"),id -> Component.literal(id.equals("test:a") ? "Translated A" : id));
        assertEquals("Heading\n- Translated A\n- test:b",detail.getString());
    }
    @Test void tooltipPreviewStopsAtThreeWithoutTruncatingTheSource() {
        var members=java.util.stream.IntStream.range(0,130).mapToObj(i->"test:"+i).toList();
        var text=Component.literal("Heading");
        AdvancementPresentation.appendMembers(text,members,Component::literal);
        assertEquals("Heading\n- test:0\n- test:1\n- test:2\n…",text.getString());
        assertEquals(130,members.size());
    }
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
        assertEquals(Component.translatable("screen.brnquest.advancement.task_title"),AdvancementPresentation.taskTitle(task.config()));
        assertEquals("Custom",AdvancementPresentation.taskTitle(Map.of("title","Custom")).getString());
        assertEquals("",new AdvancementPresentation.Task().itemSnbt(task));
        assertEquals("",new AdvancementPresentation.Reward().itemSnbt(reward));
        assertEquals(ClientTaskPresentation.NodeStyle.CUSTOM,new AdvancementPresentation.Task().nodeStyle(task));
    }
    @Test void rewardTooltipKeepsSelectionAndClaimStateWithoutMechanics() {
        var values=Map.of("title","Author title","advancement","test:one","criterion","my_criterion");
        var hint=AdvancementPresentation.rewardHint(values,true,false).getString();
        assertTrue(hint.startsWith(Component.translatable("screen.brnquest.advancement.reward_heading").getString()+"\n"));
        assertTrue(hint.contains("Author title")); assertTrue(hint.contains("\n- test:one\n")); assertTrue(hint.contains("my_criterion"));
        assertFalse(hint.contains(Component.translatable("screen.brnquest.advancement.reward_hint").getString()));
        assertTrue(hint.contains(Component.translatable("screen.brnquest.reward.click_to_claim").getString()));
        assertTrue(AdvancementPresentation.rewardHint(values,false,true).getString()
                .contains(Component.translatable("screen.brnquest.reward.claimed").getString()));
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.builtin.client.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Completion requirements must survive authored captions and changing post-submission inventory. */
class QuestNotificationContentTest {
    @Test void itemCaptionDoesNotReplaceTheRequiredItemQuantityOrConsumeSemantics() {
        var task = task("item", Map.of("title", "A short caption", "item", "{id:\"minecraft:stone\",count:1}",
                "count", "8", "consume_items", "true"));
        assertEquals(Component.translatable("screen.brnquest.task.item.require", new ItemStack(Items.STONE).getHoverName(), 8),
                BuiltinItemPresentation.completionDescription(task, new ItemStack(Items.STONE)));
        var emptyInventory = BuiltinItemPresentation.completionDescription(task, ItemStack.EMPTY);
        var contents = (TranslatableContents) emptyInventory.getContents();
        assertEquals(8, contents.getArgs()[1]);
        assertFalse(emptyInventory.getString().contains("A short caption"));
    }
    @Test void craftingRequirementUsesTheConfiguredOutputCount() {
        var task = task("item", Map.of("title", "Crafting caption", "item", "{id:\"minecraft:stone\",count:1}",
                "count", "12", "only_from_crafting", "true"));
        assertEquals(Component.translatable("message.brnquest.notification.crafted", new ItemStack(Items.STONE).getHoverName(), 12),
                BuiltinItemPresentation.completionDescription(task, new ItemStack(Items.STONE)));
    }
    @Test void encounterRequirementsIncludeTargetCountAndObservationRangeAndTime() {
        var killed = (TranslatableContents) EncounterPresentation.completionDescription(
                task("kill_entity", Map.of("title", "Caption", "target", "minecraft:zombie", "count", "7")), false).getContents();
        assertEquals("message.brnquest.notification.killed", killed.getKey());
        assertEquals(7L, killed.getArgs()[1]);
        var observed = (TranslatableContents) EncounterPresentation.completionDescription(
                task("observe", Map.of("title", "Caption", "target", "minecraft:dirt", "distance", "6", "duration", "40")), true).getContents();
        assertEquals(6.0, observed.getArgs()[1]);
        assertEquals(40, observed.getArgs()[2]);
    }
    @Test void advancementCaptionDoesNotHideTheSelectedCriterionOrGroupMode() {
        var criterion = AdvancementPresentation.completionDescription(Map.of("title", "Caption", "advancement", "test:one", "criterion", "condition")).getString();
        assertTrue(criterion.contains("test:one"));
        assertTrue(criterion.contains("condition"));
        assertFalse(criterion.contains("Caption"));
        var group = AdvancementPresentation.completionDescription(Map.of("advancement", "#test:group", "mode", "all")).getString();
        assertTrue(group.contains("#test:group"));
        assertTrue(group.contains(Component.translatable("screen.brnquest.advancement.all").getString()));
    }
    private static TaskView task(String type, Map<String, String> config) {
        return new TaskView(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:goal"),
                ResourceLocation.parse("brnquest:" + type), config, false);
    }
}

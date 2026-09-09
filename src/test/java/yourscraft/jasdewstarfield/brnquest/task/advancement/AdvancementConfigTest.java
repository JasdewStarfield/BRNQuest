package yourscraft.jasdewstarfield.brnquest.task.advancement;

import org.junit.jupiter.api.Test;
import java.util.*;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementConfigTest {
    @Test void singleCriterionAndExplicitGroupModesHaveUnambiguousSemantics() {
        assertEquals("a",AdvancementConfig.parse(Map.of("advancement","test:one","criterion","a")).criterion());
        assertFalse(AdvancementConfig.parse(Map.of("advancement","#test:group")).all());
        assertTrue(AdvancementConfig.parse(Map.of("advancement","#test:group","mode","all")).all());
        assertThrows(IllegalArgumentException.class,()->AdvancementConfig.parse(Map.of("advancement","#test:group","criterion","a")));
        assertThrows(IllegalArgumentException.class,()->AdvancementConfig.parse(Map.of("advancement","test:one","mode","random")));
        assertThrows(IllegalArgumentException.class,()->AdvancementConfig.parse(Map.of("advancement","")));
    }
    @Test void bothRegisteredTypesOwnTheirFieldsAndNormalization() {
        var task=TaskTypeRegistry.get(AdvancementConfig.ID);
        var reward=RewardTypeRegistry.get(AdvancementConfig.ID);
        assertNotNull(task); assertNotNull(reward);
        assertTrue(task.configFields().stream().anyMatch(f->f.key().equals("mode")));
        assertFalse(reward.configFields().stream().anyMatch(f->f.key().equals("mode")));
        assertTrue(task.configFields().stream().filter(f->f.key().equals("advancement")).allMatch(f->f.serverSource().orElseThrow().equals(AdvancementConfig.ID)));
        assertEquals(Map.of("advancement","test:one","mode","all","foreign","keep"),
                task.normalizeConfig(Map.of("advancement"," test:one ","mode"," ALL ","foreign","keep")));
    }
}

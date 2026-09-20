package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Old bytecode links to the new overload through its default bridge, not through recompilation. */
class LegacyNormalizationCompatibilityTest {
    @Test void preContextConsumersInvokeTheirLegacyOverrideExactlyOnce() throws Exception {
        var location = Path.of(System.getProperty("brnquest.legacyPresentationClasses")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{location}, TaskType.class.getClassLoader())) {
            assertSame(TaskType.class, loader.loadClass(TaskType.class.getName()));
            assertSame(RewardType.class, loader.loadClass(RewardType.class.getName()));
            var taskClass = loader.loadClass("legacy.LegacyTaskType");
            var rewardClass = loader.loadClass("legacy.LegacyRewardType");
            for (var consumer : java.util.List.of(taskClass, rewardClass)) {
                assertSame(loader, consumer.getClassLoader());
                assertFalse(java.util.Arrays.stream(consumer.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("normalizeConfig") && method.getParameterCount() == 2));
            }
            var task = (TaskType<?>) taskClass.getConstructor().newInstance();
            var reward = (RewardType<?>) rewardClass.getConstructor().newInstance();
            assertDoesNotThrow(() -> reward.clientClaimResponse(null, null, OperationResult.success("legacy")));
            var context = ConfigNormalizationContext.withRegistries(RegistryAccess.EMPTY);
            assertEquals(Map.of("legacy", "task"), task.normalizeConfig(context, Map.of()));
            assertEquals(Map.of("legacy", "reward"), reward.normalizeConfig(context, Map.of()));
            assertEquals(1, taskClass.getField("calls").get(task));
            assertEquals(1, rewardClass.getField("calls").get(reward));
        }
    }
}

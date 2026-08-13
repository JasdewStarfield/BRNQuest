package yourscraft.jasdewstarfield.brnquest.task;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicSpiSignatureTest {
    @Test
    void taskAndRewardSpiDoNotExposeInternalDataOrProgressPackages() {
        Stream.of(TaskType.class, RewardType.class).flatMap(type -> Arrays.stream(type.getMethods()))
                .flatMap(PublicSpiSignatureTest::signatureTypes)
                .map(Class::getName)
                .forEach(name -> {
                    assertFalse(name.contains(".brnquest.data."), name);
                    assertFalse(name.contains(".brnquest.progress."), name);
                    assertFalse(name.contains("QuestBookManager"), name);
                });
    }

    private static Stream<Class<?>> signatureTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes()));
    }
}

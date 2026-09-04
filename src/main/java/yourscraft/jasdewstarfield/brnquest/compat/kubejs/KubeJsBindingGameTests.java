package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Runtime contract checks for the common-only facade behind the optional KubeJS entry point. */
@GameTestHolder("brnquest")
public final class KubeJsBindingGameTests {
    private KubeJsBindingGameTests() {}

    @SuppressWarnings("removal") // Embedded login supplies a real server-bound player without a client process.
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void wrongThreadWritesReturnAStableResult(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        Thread worker = Thread.startVirtualThread(() -> result.set(
                BrnQuestKubeJSBindings.INSTANCE.completeQuest(player, "brnquest:missing")));
        try {
            worker.join(1_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            helper.fail("Interrupted while checking the KubeJS wrong-thread boundary");
            return;
        }

        helper.assertTrue(!worker.isAlive(), "KubeJS facade call must return without scheduling hidden work");
        helper.assertValueEqual(result.get().get("status"), "INVALID_REQUEST", "result status");
        helper.assertValueEqual(result.get().get("code"), "WRONG_THREAD", "result code");
        helper.assertValueEqual(result.get().get("changed"), false, "changed flag");
        helper.succeed();
    }
}

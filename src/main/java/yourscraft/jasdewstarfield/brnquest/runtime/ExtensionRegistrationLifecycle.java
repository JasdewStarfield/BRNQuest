package yourscraft.jasdewstarfield.brnquest.runtime;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviderRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

/** Coordinates common, client, and reserved script registration windows. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ExtensionRegistrationLifecycle {
    private static volatile boolean commonFrozen;
    private static volatile boolean clientFrozen;
    private static volatile boolean scriptFrozen;

    private ExtensionRegistrationLifecycle() {}

    /** Called once before the first server resource listener is installed. */
    @ApiStatus(ApiStability.INTERNAL)
    public static synchronized void freezeCommonAndScript() {
        if (commonFrozen && scriptFrozen) return;
        // Close the atomic plugin facade before its target registries so no late
        // companion callback can race the first task-book decode.
        BrnQuestPlugins.freeze();
        TaskTypeRegistry.freeze();
        RewardTypeRegistry.freeze();
        ProgressOwnerProviderRegistry.freeze();
        // Script types use their own candidate window and stay closed outside script evaluation.
        scriptFrozen = true;
        commonFrozen = true;
    }

    /** Client hooks freeze client-only presentation registries before marking this window. */
    @ApiStatus(ApiStability.INTERNAL)
    public static synchronized void markClientFrozen() {
        clientFrozen = true;
    }

    public static RegistrationState state() {
        return new RegistrationState(commonFrozen, clientFrozen,
                !ScriptExtensionRegistry.snapshot().registrationOpen());
    }

    /** Immutable lifecycle projection safe for diagnostics and compatibility tests. */
    @ApiStatus(ApiStability.EXPERIMENTAL)
    public record RegistrationState(boolean commonFrozen, boolean clientFrozen, boolean scriptFrozen) {}
}

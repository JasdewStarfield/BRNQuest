package yourscraft.jasdewstarfield.brnquest.runtime;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
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
        TaskTypeRegistry.freeze();
        RewardTypeRegistry.freeze();
        ProgressOwnerProviderRegistry.freeze();
        // The script registry arrives in stage 6; reserving and closing its window now
        // prevents scripts from mutating type identity midway through a book reload.
        scriptFrozen = true;
        commonFrozen = true;
    }

    /** Client hooks freeze client-only presentation registries before marking this window. */
    @ApiStatus(ApiStability.INTERNAL)
    public static synchronized void markClientFrozen() {
        clientFrozen = true;
    }

    public static RegistrationState state() {
        return new RegistrationState(commonFrozen, clientFrozen, scriptFrozen);
    }

    /** Immutable lifecycle projection safe for diagnostics and compatibility tests. */
    @ApiStatus(ApiStability.EXPERIMENTAL)
    public record RegistrationState(boolean commonFrozen, boolean clientFrozen, boolean scriptFrozen) {}
}

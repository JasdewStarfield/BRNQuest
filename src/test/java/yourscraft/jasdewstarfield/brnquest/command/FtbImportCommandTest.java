package yourscraft.jasdewstarfield.brnquest.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parse commands without executing imports or requiring a running world. */
class FtbImportCommandTest {
    @Test void diagnosticIncludesCodeSourceLocationObjectAndMessage() {
        // Keep enough context in chat to identify the failing source without opening the report.
        var diagnostic = new yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic(
                yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.FATAL,
                "BQF-001", "data.snbt", "version", "book-id", "Expected format 13");
        var message = BrnQuestCommands.importDiagnostic(diagnostic);
        assertEquals("[FATAL BQF-001] data.snbt / version / book-id: Expected format 13", message.getString());
        assertEquals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED),
                message.getStyle().getColor());
    }

    @Test void directAndWorkspaceCommandsAcceptDefaultsCustomIdsAndPreview() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        BrnQuestCommands.register(dispatcher);
        for (String prefix : new String[]{"brnquest ", "brnquest workspace "}) {
            for (String suffix : new String[]{"", " --dry-run", " mypack", " mypack --dry-run",
                    " mypack campaign", " mypack campaign --dry-run"}) {
                String command = prefix + "import_ftb_local" + suffix;
                var parsed = dispatcher.parse(command, source(2));
                assertFalse(parsed.getReader().canRead(), command);
                assertNotNull(parsed.getContext().getCommand(), command);
            }
            // The old inbox grammar must remain available, including a source named 'local'.
            var old = dispatcher.parse(prefix + "import_ftb local mypack campaign --dry-run", source(2));
            assertFalse(old.getReader().canRead());
            assertNotNull(old.getContext().getCommand());
            var denied = dispatcher.parse(prefix + "import_ftb_local", source(0));
            assertTrue(denied.getReader().canRead());
            assertNull(denied.getContext().getCommand());
        }
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "test", Component.literal("test"), null, null);
    }
}

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
    @Test void summariesSeparateBlockingFatalsFromRecoverableErrors() {
        var report = new yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport();
        var book = new yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition(
                net.minecraft.resources.ResourceLocation.parse("test:main"), 1, "Book",
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        var result = new yourscraft.jasdewstarfield.brnquest.compat.ftb.FtbImportResult(book, report,
                0, 2, 3, 0, 0, java.util.List.of());
        // A recoverable error still reports a completed import; warnings remain a distinct count.
        report.add(new yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic(
                yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.ERROR, "TEST", "", "", "", "Partial loss"));
        report.add(new yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic(
                yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.WARN, "TEST", "", "", "", "Layout loss"));
        var completed = BrnQuestCommands.importSummary(result, false);
        var content = assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, completed.getContents());
        assertEquals("command.brnquest.import.summary", content.getKey());
        assertArrayEquals(new Object[]{2, 3, 0L, 1L, 1L}, content.getArgs());
        assertEquals("command.brnquest.import.summary_dry_run", assertInstanceOf(
                net.minecraft.network.chat.contents.TranslatableContents.class,
                BrnQuestCommands.importSummary(result, true).getContents()).getKey());

        // Fatal results must never say that a draft was imported, even during a preview.
        report.add(new yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic(
                yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.FATAL, "TEST", "", "", "", "Invalid book"));
        for (boolean preview : new boolean[]{false, true}) {
            var blocked = BrnQuestCommands.importSummary(result, preview);
            var blockedContent = assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, blocked.getContents());
            assertEquals("command.brnquest.import.summary_fatal", blockedContent.getKey());
            assertArrayEquals(new Object[]{2, 3, 1L, 1L, 1L}, blockedContent.getArgs());
            assertEquals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED),
                    blocked.getStyle().getColor());
        }
    }

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

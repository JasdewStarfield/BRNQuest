package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Uses the real disk/lease/reload boundary in a separate batch; no manual acceptance world is touched. */
@GameTestHolder("brnquest")
public final class LiveEditGameTests {
    private LiveEditGameTests() {}

    @SuppressWarnings("removal") // Embedded login exercises real online-player permission checks.
    @GameTest(template = "empty", timeoutTicks = 400, batch = "liveEditorWorkflow")
    @PrefixGameTestTemplate(false)
    public static void liveEditsPersistUndoAndReloadWithoutPublishingDrafts(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var manager = QuestBookManager.get();
        var previous = manager.active().orElseThrow();
        var previousResource = manager.activeResource().orElse(null);
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        ServerPlayer ordinary = helper.makeMockServerPlayerInLevel();
        server.getPlayerList().getOps().add(new ServerOpListEntry(admin.getGameProfile(), 2, false));
        server.getPlayerList().sendPlayerPermissionLevel(admin);
        var sessions = EditSessionService.get();
        var edits = new DraftEditService();
        String unique = admin.getUUID().toString().replace("-", "");
        ResourceLocation bookId = ResourceLocation.parse("brnquest:live_" + unique);
        ResourceLocation source = ResourceLocation.parse("000_live:source_" + unique);
        var original = new QuestBookDefinition(bookId, 1, "Original", List.of(), List.of(), Map.of());
        Path file = WorkspacePaths.deployed(server).resolve("data/000_live/brnquest/books/source_" + unique + ".json");
        Path selectionFile = ActiveBookSelection.file(WorkspacePaths.deployed(server));
        String previousSelection;
        try { previousSelection = Files.exists(selectionFile)
                ? Files.readString(selectionFile, StandardCharsets.UTF_8) : null; }
        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        Runnable cleanup = () -> {
            sessions.releasePlayer(server, admin.getUUID());
            if (server.getPlayerList().getPlayer(admin.getUUID()) == admin) server.getPlayerList().remove(admin);
            if (server.getPlayerList().getPlayer(ordinary.getUUID()) == ordinary) server.getPlayerList().remove(ordinary);
            server.getPlayerList().getOps().remove(admin.getGameProfile());
            // The unique filename belongs only to this test; do not delete the shared managed pack.
            try {
                Files.deleteIfExists(file);
                if (previousSelection == null) Files.deleteIfExists(selectionFile);
                else Files.writeString(selectionFile, previousSelection, StandardCharsets.UTF_8);
            }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            manager.install(previous.book(), new DiagnosticReport(), previousResource);
        };
        try {
            helper.assertTrue(manager.install(original, new DiagnosticReport(), source), "fixture is valid");
            // This isolated source is the active book for the test. Mirror that selection in
            // the deployed pack so a later resource reload follows the same live source.
            Files.createDirectories(selectionFile.getParent());
            Files.writeString(selectionFile, ActiveBookSelection.encode(source), StandardCharsets.UTF_8);
            String initial = manager.active().orElseThrow().revision();
            helper.assertTrue(!sessions.openLive(ordinary, bookId).success(), "ordinary players cannot enter live editing");
            // An existing advanced draft must remain untouched and must not become the live editor's source.
            var savedDraft = new DraftService().createEmpty(admin, bookId, "Unfinished advanced draft");
            helper.assertTrue(savedDraft.success(), "independent advanced draft created: " + savedDraft.code() + " " + savedDraft.message());
            var opened = sessions.openLive(admin, bookId);
            helper.assertTrue(opened.success(), "live session opens on published content");
            var token = opened.value().sessionId();
            helper.assertTrue(!sessions.open(admin, savedDraft.value()).success(), "cannot relabel a live lease as advanced");
            long generation = manager.liveGeneration();
            var edited = edits.setBookTitle(admin, token, bookId, initial, "即时编辑标题");
            helper.assertTrue(edited.success(), "edit saves and activates: " + edited.code() + " " + edited.message());
            String revision = edited.value().snapshot().draftRevision();
            helper.assertValueEqual(manager.active().orElseThrow().revision(), revision, "runtime changes immediately");
            helper.assertValueEqual(manager.liveGeneration(), generation + 1, "one operation installs exactly once");
            helper.assertValueEqual(Files.readString(file, StandardCharsets.UTF_8), NativeBookJson.encode(edited.value().snapshot().book()),
                    "disk matches authoritative live revision");
            helper.assertTrue(!sessions.inspect(admin, bookId).value().dirty(), "no manual save is needed");
            helper.assertTrue(new DraftPersistenceService().save(admin, token, bookId, revision).code().equals("LIVE_ALREADY_SAVED"),
                    "live sessions cannot overwrite author drafts");
            helper.assertValueEqual(new DraftRepository().load(server, bookId).value().book().title(),
                    "Unfinished advanced draft", "advanced draft remains isolated");
            helper.assertTrue(!edits.setBookTitle(admin, token, bookId, revision, "").success(), "invalid title rejected");
            helper.assertValueEqual(manager.active().orElseThrow().revision(), revision, "failures preserve runtime");
            helper.assertTrue(sessions.undo(admin, token, bookId, revision).success(), "undo commits to disk and runtime");
            helper.assertValueEqual(manager.active().orElseThrow().revision(), initial, "undo restores original content");
            helper.assertTrue(sessions.redo(admin, token, bookId, initial).success(), "redo commits normally");
            helper.assertValueEqual(manager.active().orElseThrow().revision(), revision, "redo restores edited content");
            helper.assertTrue(!edits.setBookTitle(admin, token, bookId, initial, "stale").success(), "stale request rejected");
            helper.assertValueEqual(sessions.inspect(admin, bookId).value().undoSteps(), 0,
                    "existing stale-session recovery semantics clear history instead of replaying an uncertain timeline");
            // Simulate a file changed outside the session; a failed save must not advance history or runtime.
            String stored = Files.readString(file, StandardCharsets.UTF_8);
            Files.writeString(file, NativeBookJson.encode(original), StandardCharsets.UTF_8);
            helper.assertTrue(!edits.setBookTitle(admin, token, bookId, revision, "must fail").success(), "disk conflict rejected");
            helper.assertValueEqual(manager.active().orElseThrow().revision(), revision, "disk conflict preserves active book");
            Files.writeString(file, stored, StandardCharsets.UTF_8);
            sessions.close(admin, token, revision);
            var advanced = sessions.open(admin, savedDraft.value());
            helper.assertTrue(advanced.success(), "advanced mode remains available after live editing");
            var advancedEdit = edits.setBookTitle(admin, advanced.value().sessionId(), bookId,
                    savedDraft.value().draftRevision(), "Still unpublished");
            helper.assertTrue(advancedEdit.success(), "advanced edits work");
            helper.assertValueEqual(manager.active().orElseThrow().revision(), revision, "advanced edit never activates");
            sessions.releasePlayer(server, admin.getUUID());
            helper.assertTrue(server.getPackRepository().getSelectedIds().contains(WorkspacePaths.PACK_ID), "world pack selected for restart");
            var reloaded = server.reloadResources(server.getPackRepository().getSelectedIds());
            helper.succeedWhen(() -> {
                helper.assertTrue(reloaded.isDone(), "resource reload still running");
                try {
                    reloaded.join();
                    helper.assertValueEqual(manager.active().orElseThrow().revision(), revision,
                            "actual reload reads the edited source path, not the unrelated advanced draft");
                } finally { cleanup.run(); }
            });
        } catch (Exception | AssertionError exception) {
            cleanup.run();
            throw new IllegalStateException("Live edit integration failed", exception);
        }
    }
}

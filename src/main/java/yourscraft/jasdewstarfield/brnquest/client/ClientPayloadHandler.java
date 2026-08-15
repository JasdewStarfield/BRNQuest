package yourscraft.jasdewstarfield.brnquest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.QuestScreen;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;

/** Marshals network updates onto the render thread before touching client state or screens. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}
    public static void hello(BrnQuestNetwork.HelloPayload payload) {
        Minecraft.getInstance().execute(() -> {
            String known = ClientQuestState.get().revision();
            if (!payload.revision().equals(known)) BrnQuestNetwork.requestBook(known);
        });
    }
    public static void manifest(BrnQuestNetwork.BookManifestPayload payload) { Minecraft.getInstance().execute(() -> ClientQuestState.get().begin(payload.revision(), payload.chunks(), payload.decodedBytes())); }
    public static void chunk(BrnQuestNetwork.BookChunkPayload payload) { Minecraft.getInstance().execute(() -> ClientQuestState.get().acceptChunk(payload.revision(), payload.index(), payload.data())); }
    public static void progress(BrnQuestNetwork.ProgressSnapshotPayload payload) {
        Minecraft.getInstance().execute(() -> {
            ClientQuestState.get().progress(payload.json());
        });
    }
    public static void delta(BrnQuestNetwork.ProgressDeltaPayload payload) { Minecraft.getInstance().execute(() -> ClientQuestState.get().progress(payload.json())); }
    public static void toast(BrnQuestNetwork.QuestToastPayload payload) {
        // Protocol 2 retains the payload type, but generic progress toasts are intentionally suppressed.
    }
    public static void open(BrnQuestNetwork.OpenScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            ResourceLocation selected = ResourceLocation.tryParse(payload.questId());
            if (selected != null) ClientQuestState.get().selected(selected);
            Minecraft.getInstance().setScreen(new QuestScreen());
        });
    }

    public static void editorCatalog(AuthoringNetwork.CatalogPayload payload) {
        Minecraft.getInstance().execute(() -> ClientEditorState.get().acceptCatalog(payload.json()));
    }

    public static void editorSession(AuthoringNetwork.SessionPayload payload) {
        Minecraft.getInstance().execute(() -> {
            ClientEditorState state = ClientEditorState.get();
            state.acceptSession(payload.json()).ifPresent(AuthoringNetwork::openSession);
            state.pollImmediateClose().ifPresent(request ->
                    AuthoringNetwork.closeSession(request.sessionId(), request.draftRevision()));
        });
    }

    public static void editorDraftChunk(AuthoringNetwork.DraftChunkPayload payload) {
        Minecraft.getInstance().execute(() -> ClientEditorState.get().acceptDraftChunk(
                payload.sessionId(), payload.revision(), payload.index(), payload.data()));
    }
}

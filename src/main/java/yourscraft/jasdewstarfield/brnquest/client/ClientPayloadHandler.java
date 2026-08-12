package yourscraft.jasdewstarfield.brnquest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.QuestScreen;
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
            if (payload.toast()) SystemToast.add(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("toast.brnquest.updated"), Component.translatable("toast.brnquest.updated.description"));
        });
    }
    public static void delta(BrnQuestNetwork.ProgressDeltaPayload payload) { Minecraft.getInstance().execute(() -> ClientQuestState.get().progress(payload.json())); }
    public static void toast(BrnQuestNetwork.QuestToastPayload payload) {
        Minecraft.getInstance().execute(() -> SystemToast.add(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("toast.brnquest.updated"), Component.translatable("toast.brnquest.updated.description")));
    }
    public static void open(BrnQuestNetwork.OpenScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            ResourceLocation selected = ResourceLocation.tryParse(payload.questId());
            if (selected != null) ClientQuestState.get().selected(selected);
            Minecraft.getInstance().setScreen(new QuestScreen());
        });
    }
}

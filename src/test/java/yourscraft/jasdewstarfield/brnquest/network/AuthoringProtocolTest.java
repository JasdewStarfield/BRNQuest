package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.*;
import static org.junit.jupiter.api.Assertions.*;

/** Records actual registration calls so the manifest cannot silently drift away from the wiring. */
class AuthoringProtocolTest {
    private record Expected(Class<?> payload, String path, boolean serverbound) {}
    private static final List<Expected> EXPECTED = List.of(
            new Expected(OpenLivePayload.class, "editor_open_live", true),
            new Expected(RequestCatalogPayload.class, "editor_catalog_request", true),
            new Expected(OpenSessionPayload.class, "editor_session_open", true),
            new Expected(OpenCurrentSessionPayload.class, "editor_session_open_current", true),
            new Expected(RenewSessionPayload.class, "editor_session_renew", true),
            new Expected(CloseSessionPayload.class, "editor_session_close", true),
            new Expected(RecoverSessionPayload.class, "editor_session_recover", true),
            new Expected(SaveSessionPayload.class, "editor_session_save", true),
            new Expected(PublishApplyPayload.class, "editor_publish_apply", true),
            new Expected(UpdateQuestPayload.class, "editor_quest_update", true),
            new Expected(EditorMutationPayload.class, "editor_mutation", true),
            new Expected(CatalogPayload.class, "editor_catalog", false),
            new Expected(SessionPayload.class, "editor_session", false),
            new Expected(DraftChunkPayload.class, "editor_draft_chunk", false));

    @Test void registrationOrderDirectionIdsAndCodecsRemainCompatible() throws Exception {
        assertEquals("9", BrnQuestConstants.NETWORK_PROTOCOL);
        var recorder = new RecordingRegistrar();
        AuthoringNetwork.register(recorder);
        assertEquals(14, recorder.routes.size());
        assertEquals(11, recorder.routes.stream().filter(Route::serverbound).count());
        var unique = new HashSet<String>();
        for (int index = 0; index < EXPECTED.size(); index++) {
            var expected = EXPECTED.get(index);
            var actual = recorder.routes.get(index);
            assertEquals("brnquest:" + expected.path(), actual.type().id().toString());
            assertTrue(unique.add(actual.type().id().toString()));
            assertEquals(expected.serverbound(), actual.serverbound());
            assertSame(expected.payload().getField("TYPE").get(null), actual.type());
            assertSame(expected.payload().getField("CODEC").get(null), actual.codec());
            roundTrip(expected.payload(), actual.codec());
        }
        assertEquals(14, Arrays.stream(AuthoringNetwork.class.getDeclaredClasses())
                .filter(CustomPacketPayload.class::isAssignableFrom).count());
        assertEquals(10, Arrays.stream(AuthoringNetwork.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().endsWith("Wire")).count());
    }

    @SuppressWarnings("unchecked")
    private static void roundTrip(Class<?> type, Object codecObject) throws Exception {
        // Different field values detect accidental field reordering as well as omitted fields.
        var components = type.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(c -> c.getType()).toArray(Class<?>[]::new);
        Object[] values = new Object[types.length];
        for (int index = 0; index < types.length; index++) {
            values[index] = types[index] == String.class ? "field_" + index + "中文"
                    : types[index] == boolean.class ? true : 7;
        }
        var payload = (CustomPacketPayload) type.getDeclaredConstructor(types).newInstance(values);
        var codec = (StreamCodec<ByteBuf, CustomPacketPayload>) codecObject;
        var buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, payload);
            assertEquals(payload, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void mutationNamesAreExactAndUnknownActionsAreRejected() {
        var expected = List.of("UNDO", "REDO", "ADD_GROUP", "UPDATE_GROUP", "MOVE_GROUP", "DELETE_GROUP", "ADD_CHAPTER", "UPDATE_CHAPTER", "MOVE_CHAPTER", "DELETE_CHAPTER", "ADD_QUEST", "COPY_QUEST", "DELETE_QUEST", "MOVE_QUESTS", "UPDATE_QUEST_TRANSLATION", "ADD_DEPENDENCY", "REMOVE_DEPENDENCY", "ADD_TASK", "UPDATE_TASK", "COPY_TASK", "MOVE_TASK", "DELETE_TASK", "ADD_REWARD", "UPDATE_REWARD", "COPY_REWARD", "MOVE_REWARD", "DELETE_REWARD");
        assertEquals(27, expected.size());
        assertEquals(28, AuthoringMutationAction.values().length); // REVIEW shares the envelope.
        for (String name : expected) assertEquals(name, AuthoringMutationAction.fromWire(name).orElseThrow().wireName());
        assertEquals(AuthoringMutationAction.REVIEW, AuthoringMutationAction.fromWire("REVIEW").orElseThrow());
        for (String invalid : Arrays.asList(null, "", "undo", "UNDO ", "UNKNOWN")) {
            assertTrue(AuthoringMutationAction.fromWire(invalid).isEmpty());
        }
    }

    record Route(CustomPacketPayload.Type<?> type, Object codec, boolean serverbound, IPayloadHandler<?> handler) {}

    /** Overrides only play routes; no global NeoForge registration state is changed by these tests. */
    static final class RecordingRegistrar extends PayloadRegistrar {
        final List<Route> routes = new ArrayList<>();
        RecordingRegistrar() { super(BrnQuestConstants.NETWORK_PROTOCOL); }
        @Override public <T extends CustomPacketPayload> PayloadRegistrar playToServer(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                IPayloadHandler<T> handler) {
            routes.add(new Route(type, codec, true, handler));
            return this;
        }
        @Override public <T extends CustomPacketPayload> PayloadRegistrar playToClient(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                IPayloadHandler<T> handler) {
            routes.add(new Route(type, codec, false, handler));
            return this;
        }
    }
}

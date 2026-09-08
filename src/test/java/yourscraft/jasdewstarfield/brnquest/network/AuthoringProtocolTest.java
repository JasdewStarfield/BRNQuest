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

    @Test void serverRegistrationNeverResolvesTheClientDelegate() throws Exception {
        assertEquals(net.neoforged.api.distmarker.Dist.DEDICATED_SERVER,
                net.neoforged.fml.loading.FMLEnvironment.dist);
        String registrarName = "yourscraft.jasdewstarfield.brnquest.network.AuthoringPayloadRegistrar";
        var commonOwners = java.util.Set.of(registrarName, AuthoringResponseSender.class.getName(),
                AuthoringRequestDecoder.class.getName(), AuthoringSessionHandler.class.getName(),
                AuthoringPublicationHandler.class.getName());
        // Load a fresh registrar with a loader that fails even on an attempted client resolution.
        ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.contains("ClientDelegate") || name.contains(".client.")) {
                    throw new AssertionError("Dedicated server tried to resolve " + name);
                }
                if (!commonOwners.contains(name)) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        assertNotNull(input);
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        for (String owner : commonOwners) {
            assertTrue(Class.forName(owner, true, isolated).getDeclaredMethods().length > 0);
        }
        var register = Class.forName(registrarName, true, isolated)
                .getDeclaredMethod("register", PayloadRegistrar.class);
        register.setAccessible(true);
        var recorder = new RecordingRegistrar();
        register.invoke(null, recorder);
        assertEquals(14, recorder.routes.size());
    }

    @Test @SuppressWarnings("unchecked")
    void wrongPlayerSideAndServerSideClientCallbacksNeverExecuteUseCases() throws Exception {
        var recorder = new RecordingRegistrar();
        AuthoringNetwork.register(recorder);
        var context = (net.neoforged.neoforge.network.handling.IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{net.neoforged.neoforge.network.handling.IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return null;
                    throw new AssertionError("Rejected context must not execute " + method.getName());
                });
        for (var route : recorder.routes) {
            // Every C2S callback must reject a non-server player before reading its payload.
            ((IPayloadHandler<CustomPacketPayload>) route.handler()).handle(null, context);
        }
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

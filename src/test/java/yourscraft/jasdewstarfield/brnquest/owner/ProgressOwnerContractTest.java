package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProgressOwnerContractTest {
    private static final ResourceLocation TEAM_ID = ResourceLocation.fromNamespaceAndPath("owner_test", "team");

    @Test
    @Order(1)
    void registryUsesFullProviderIdAndRejectsDuplicates() {
        ProgressOwnerProvider provider = provider(TEAM_ID);
        ProgressOwnerProviderRegistry.register(provider);

        assertSame(provider, ProgressOwnerProviderRegistry.get(TEAM_ID));
        assertEquals(null, ProgressOwnerProviderRegistry.get(
                ResourceLocation.fromNamespaceAndPath("foreign", "team")));
        assertThrows(IllegalArgumentException.class, () -> ProgressOwnerProviderRegistry.register(provider(TEAM_ID)));
    }

    @Test
    @Order(2)
    void ownerViewsAndArchivesDefensivelyCopyMembers() {
        UUID ownerUuid = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var mutable = new java.util.HashSet<>(Set.of(member));
        ProgressOwnerId id = new ProgressOwnerId(TEAM_ID, ownerUuid);
        ProgressOwnerView view = new ProgressOwnerView(id, mutable, ProgressOwnerLifecycle.ACTIVE);
        ProgressOwnerArchive archive = new ProgressOwnerArchive(id, mutable, 1L, "disbanded");
        mutable.clear();

        assertEquals(Set.of(member), view.members());
        assertEquals(Set.of(member), archive.members());
        assertThrows(UnsupportedOperationException.class, () -> view.members().clear());
    }

    @Test
    @Order(3)
    void publicOwnerSpiDoesNotExposeProgressStorage() {
        Stream.of(ProgressOwnerId.class, ProgressOwnerView.class, ProgressOwnerArchive.class,
                        ProgressOwnerProvider.class, ProgressOwnerProviderRegistry.class, ProgressOwnerService.class)
                .flatMap(type -> Stream.of(type.getMethods()))
                .flatMap(ProgressOwnerContractTest::signatureTypes)
                .forEach(type -> assertFalse(type.getPackageName().contains(".progress"),
                        () -> "Owner SPI leaks internal progress type: " + type.getName()));
    }

    @Test
    @Order(4)
    void registryFreezesBeforeReload() {
        ProgressOwnerProviderRegistry.freeze();
        assertThrows(IllegalStateException.class, () -> ProgressOwnerProviderRegistry.register(provider(
                ResourceLocation.fromNamespaceAndPath("owner_test", "late"))));
    }

    private static Stream<Class<?>> signatureTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()));
    }

    private static ProgressOwnerProvider provider(ResourceLocation id) {
        return new ProgressOwnerProvider() {
            public ResourceLocation id() { return id; }
            public Optional<ProgressOwnerId> resolve(ServerPlayer player) { return Optional.empty(); }
            public Set<UUID> members(MinecraftServer server, ProgressOwnerId owner) { return Set.of(); }
            public ProgressOwnerLifecycle lifecycle(MinecraftServer server, ProgressOwnerId owner) {
                return ProgressOwnerLifecycle.UNAVAILABLE;
            }
            public Optional<ProgressOwnerArchive> archivedSnapshot(MinecraftServer server, ProgressOwnerId owner) {
                return Optional.empty();
            }
        };
    }
}

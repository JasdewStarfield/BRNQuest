package yourscraft.jasdewstarfield.brnquest.opactest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.ServerParty;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import java.util.*;

/** Real OPAC API/lifecycle and restart fixture, kept out of the published mod. */
@EventBusSubscriber(modid = "brnquest_opac_test")
public final class OpenPacSmoke {
    private static final UUID A = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("b0000000-0000-0000-0000-000000000003");
    private static final UUID SAVED_ID = UUID.fromString("b0000000-0000-0000-0000-000000000004");
    private static boolean ran;
    private static boolean completed;
    private static ProgressOwnerId deletedOwner;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (completed || event.getServer().getTickCount() < 40) return;
        String phase = System.getProperty("brnquest.opac.smoke", "");
        try {
            if (!ran) {
                ran = true;
                install();
                if (phase.equals("restart")) restart(event.getServer()); else {
                    initial(event.getServer());
                    return; // Let the actual server reconciler discover deletion, including hook-disabled polling.
                }
            } else if (event.getServer().getTickCount() < 120) return;
            if (!phase.equals("restart")) check(QuestProgressData.get(event.getServer()).archive(deletedOwner).isPresent(),
                    "actual scheduled reconciliation archives offline party deletion");
            BRNQuest.LOGGER.info("[BRNQuest/OPAC_SMOKE] {} PASS", phase);
        } catch (Throwable failure) {
            BRNQuest.LOGGER.error("[BRNQuest/OPAC_SMOKE] FAILED", failure);
        }
        completed = true;
        event.getServer().halt(false);
    }
    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        return new ServerPlayer(server, server.overworld(), new GameProfile(id, name), ClientInformation.createDefault());
    }
    private static void initial(MinecraftServer server) {
        var manager = OpenPACServerAPI.get(server).getPartyManager();
        // Re-running the initial phase deliberately resets only the three fixture identities.
        for (UUID uuid : List.of(A, B, C)) manager.removePartyByOwner(uuid);
        var a = player(server, A, "OpacA"); var b = player(server, B, "OpacB"); var c = player(server, C, "OpacC");
        var data = QuestProgressData.get(server);
        var personal = ProgressOwnerService.require(a);
        check(personal.providerId().equals(ProgressOwnerProviders.PERSONAL), "ungrouped fallback");
        data.get(personal).addTaskProgress("opac_test:history", 7);
        long before = OwnerInvalidation.generation();
        var party = manager.createPartyForOwner(a.getGameProfile());
        var owner = ProgressOwnerService.require(a);
        check(owner.ownerId().equals(party.getId()), "stable party ID");
        check(!owner.equals(personal), "new team has separate ledger");
        verifyFailureFallback(a, owner, personal);
        check(ProgressEngine.get().progress(a).taskProgress("opac_test:history") == 0, "no personal merge");
        party.addMember(B, PartyMemberRank.MEMBER, "OpacB");
        check(ProgressOwnerService.resolve(a).orElseThrow().members().equals(Set.of(A, B)), "complete offline member stream");
        check(ProgressOwnerService.require(b).equals(owner), "write-before-poll join resolution");
        data.tracked(A, "");
        data.tracked(B, "");
        OwnerRuntime.login(a);
        OwnerRuntime.login(b);
        ProgressEngine.get().toggleTracked(a, id("focus"));
        check(ProgressEngine.get().visibleStatuses(a).get("opac_test:focus") == QuestStatus.ACTIVE, "personal focus A");
        check(ProgressEngine.get().visibleStatuses(b).get("opac_test:focus") == QuestStatus.AVAILABLE, "focus never leaks to B");
        check(ProgressEngine.get().forceComplete(a, id("quest")).changed(), "shared completion");
        check(ProgressEngine.get().claim(a, id("ordinary")).changed(), "A ordinary reward");
        check(BrnQuestApi.getProgress(b, "opac_test:quest").orElseThrow().claimedRewards().isEmpty(), "B independent receipt");
        check(ProgressEngine.get().claim(b, id("ordinary")).changed(), "offline member ordinary reward");
        check(!ProgressEngine.get().claim(a, id("ordinary")).changed(), "ordinary idempotency");
        check(ProgressEngine.get().claim(b, id("team")).changed(), "one team reward");
        check(!ProgressEngine.get().claim(a, id("team")).changed(), "team idempotency");
        check(a.totalExperience == 1 && b.totalExperience == 3, "actual per-recipient side effects");
        // Automatic delivery and repeat blocking both include an offline completion member.
        check(ProgressEngine.get().forceComplete(a, id("automatic")).changed(), "automatic completion");
        ProgressEngine.get().tick(b);
        check(a.totalExperience == 6 && b.totalExperience == 8, "automatic offline-member catch-up");
        check(ProgressEngine.get().forceComplete(a, id("repeat")).changed(), "repeat completion");
        check(ProgressEngine.get().claim(a, id("repeat_reward")).changed(), "first repeat receipt");
        check(ProgressEngine.get().progress(a).status("opac_test:repeat") == QuestStatus.COMPLETED, "offline member blocks repeat");
        check(ProgressEngine.get().claim(b, id("repeat_reward")).changed(), "second repeat receipt");
        check(ProgressEngine.get().progress(a).status("opac_test:repeat") == QuestStatus.AVAILABLE, "all members permit next repeat");
        party.addMember(C, PartyMemberRank.MEMBER, "OpacC");
        check(ProgressEngine.get().claim(c, id("ordinary")).code().equals("NOT_ELIGIBLE"), "late join has no old-cycle entitlement");
        // Owner transfer has no public mutator; this fixture alone exercises the audited internal operation.
        check(((ServerParty) party).changeOwner(B, "OpacB"), "transfer owner");
        check(ProgressOwnerService.require(a).equals(owner), "transfer preserves ledger identity");
        party.removeMember(A);
        check(ProgressOwnerService.require(a).equals(personal), "leave resumes personal");
        check(ProgressEngine.get().progress(a).taskProgress("opac_test:history") >= 7, "personal history retained");
        party.addMember(A, PartyMemberRank.MEMBER, "OpacA");
        check(!ProgressEngine.get().claim(a, id("ordinary")).changed(), "rejoin never duplicates reward");
        data.observe(owner, Set.of(A, B, C), ProgressOwnerLifecycle.ACTIVE);
        manager.removePartyById(party.getId());
        var provider = ProgressOwnerProviderRegistry.get(owner.providerId());
        check(provider.lifecycle(server, owner) == ProgressOwnerLifecycle.ARCHIVED, "deleted party lifecycle");
        deletedOwner = owner;
        check(data.get(owner).memberClaimed(A, "opac_test:ordinary"), "archive preserves receipts");
        check(ProgressOwnerService.require(b).providerId().equals(ProgressOwnerProviders.PERSONAL), "delete fallback");
        var durable = manager.createPartyForOwner(a.getGameProfile());
        durable.addMember(B, PartyMemberRank.MEMBER, "OpacB");
        var durableOwner = ProgressOwnerService.require(a);
        data.get(durableOwner).addTaskProgress("opac_test:restart", 9);
        data.tracked(SAVED_ID, durable.getId().toString());
        data.setDirty();
        if (Boolean.getBoolean("brnquest.opac.disableHooks")) {
            check(OwnerInvalidation.generation() == before, "disabled hook really absent");
        } else check(OwnerInvalidation.generation() > before && Integer.getInteger("brnquest.opac.hookCount", 0) >= 5,
                "all audited hook sites injected");
    }
    /** Fault injection is fixture-local and restores the frozen registry even if an assertion fails. */
    @SuppressWarnings("unchecked")
    private static void verifyFailureFallback(ServerPlayer player, ProgressOwnerId team, ProgressOwnerId personal) {
        try {
            var field = ProgressOwnerProviderRegistry.class.getDeclaredField("PROVIDERS");
            field.setAccessible(true);
            var providers = (Map<ResourceLocation, ProgressOwnerProvider>) field.get(null);
            var original = providers.get(team.providerId());
            var failing = new ProgressOwnerProvider() {
                public ResourceLocation id() { return team.providerId(); }
                public Optional<ProgressOwnerId> resolve(ServerPlayer ignored) { throw new IllegalStateException("intentional OPAC query failure"); }
                public Set<UUID> members(MinecraftServer server, ProgressOwnerId owner) { return Set.of(); }
                public ProgressOwnerLifecycle lifecycle(MinecraftServer server, ProgressOwnerId owner) { return ProgressOwnerLifecycle.UNAVAILABLE; }
                public Optional<ProgressOwnerArchive> archivedSnapshot(MinecraftServer server, ProgressOwnerId owner) { return Optional.empty(); }
            };
            try {
                providers.put(team.providerId(), failing);
                check(ProgressOwnerService.require(player).equals(personal), "query exception falls back to personal history");
                check(QuestProgressData.get(player.getServer()).archive(team).isEmpty(), "query exception is not deletion");
            } finally { providers.put(team.providerId(), original); }
            check(ProgressOwnerService.require(player).equals(team), "provider recovery resumes stable team");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void restart(MinecraftServer server) {
        var a = player(server, A, "OpacA");
        var data = QuestProgressData.get(server);
        UUID expected = UUID.fromString(data.tracked(SAVED_ID));
        check(ProgressOwnerService.require(a).ownerId().equals(expected), "party UUID survives actual restart");
        check(ProgressEngine.get().progress(a).taskProgress("opac_test:restart") == 9, "shared progress survives actual restart");
        check(data.ownerIds().stream().anyMatch(id -> data.archive(id).isPresent()
                && data.get(id).memberClaimed(A, "opac_test:ordinary")), "archive and receipts survive restart");
        check(ProgressOwnerService.resolve(a).orElseThrow().members().equals(Set.of(A, B)), "offline membership survives restart");
    }
    private static void install() {
        var ordinary = new RewardDefinition(id("book"), id("ordinary"), ResourceLocation.parse("brnquest:xp"), Map.of("xp", "1"), "manual", false);
        var team = new RewardDefinition(id("book"), id("team"), ResourceLocation.parse("brnquest:xp"), Map.of("xp", "2"), "manual", true);
        var quest = new QuestDefinition(id("book"), id("quest"), id("chapter"), "Shared", "", "", "", 0, 0,
                List.of(), List.of(), List.of(ordinary, team), "");
        var focus = new QuestDefinition(id("book"), id("focus"), id("chapter"), "Focus", "", "", "", 1, 0,
                List.of(), List.of(), List.of(), "");
        var automatic = new QuestDefinition(id("book"), id("automatic"), id("chapter"), "Automatic", "", "", "", 2, 0,
                List.of(), List.of(), List.of(new RewardDefinition(id("book"), id("auto_reward"), ResourceLocation.parse("brnquest:xp"),
                Map.of("xp", "5"), "auto_silent", false)), "");
        var repeat = new QuestDefinition(id("book"), id("repeat"), id("chapter"), "Repeat", "", "", "", 3, 0,
                List.of(), List.of(), List.of(new RewardDefinition(id("book"), id("repeat_reward"), ResourceLocation.parse("brnquest:xp"),
                Map.of("xp", "1"), "manual", false)), "", QuestAppearance.DEFAULT,
                new QuestBehavior(false, false, false, 0, false, false, false, DependencyRequirement.ALL_COMPLETED,
                        0, false, true, 0, false), Map.of());
        var book = new QuestBookDefinition(id("book"), 1, "OPAC fixture", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, List.of(quest, focus, automatic, repeat))), Map.of());
        check(QuestBookManager.get().install(book, new DiagnosticReport()), "fixture book valid");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("opac_test", path); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

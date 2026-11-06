package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure repeat and reward eligibility checks need neither a player connection nor an engine transaction. */
class ProgressQueriesTest {
    private final ProgressQueries queries = new ProgressQueries();
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test void repeatWaitsForEveryFrozenRecipientAndTheExactCooldownBoundary() {
        var reward = new RewardDefinition(id("book"), id("reward"), id("type"), Map.of(), "manual", false);
        var quest = quest(List.of(reward), true, false);
        var progress = new PlayerProgress();
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        progress.completedCycle(quest.id().toString(), 100, 200);
        progress.completionMembers(quest.id().toString(), Set.of(A, B));
        progress.claimMember(A, reward.id().toString());
        assertFalse(queries.rewardsResolved(quest, progress));
        assertFalse(queries.repeatReady(quest, progress, 500), "An offline member still blocks reopening");
        progress.claimMember(B, reward.id().toString());
        assertTrue(queries.rewardsResolved(quest, progress));
        assertFalse(queries.repeatReady(quest, progress, 199));
        var before = progress.save();
        assertTrue(queries.repeatReady(quest, progress, 200));
        assertEquals(before, progress.save(), "A readiness query never advances or clears the cycle");
    }

    @Test void teamRewardsUseOneReceiptAndPersonalOwnersUseTheirOwnLedger() {
        var teamReward = new RewardDefinition(id("book"), id("reward"), id("type"), Map.of(), "manual", true);
        var quest = quest(List.of(teamReward), true, false);
        var progress = new PlayerProgress();
        progress.completionMembers(quest.id().toString(), Set.of(A, B));
        progress.claimMember(A, teamReward.id().toString());
        assertFalse(queries.rewardsResolved(quest, progress));
        progress.claim(teamReward.id().toString());
        assertTrue(queries.rewardsResolved(quest, progress));
        var personalReward = new RewardDefinition(id("book"), id("personal"), id("type"), Map.of(), "manual", false);
        var personalQuest = quest(List.of(personalReward), true, false);
        var personal = new PlayerProgress();
        assertFalse(queries.rewardsResolved(personalQuest, personal));
        personal.claim(personalReward.id().toString());
        assertTrue(queries.rewardsResolved(personalQuest, personal));
    }

    @Test void ignoreRewardBlockingStillRequiresTerminalStateAndDoesNotMakeOrdinaryQuestsRepeat() {
        var reward = new RewardDefinition(id("book"), id("reward"), id("type"), Map.of(), "manual", false);
        var quest = quest(List.of(reward), true, true);
        var progress = new PlayerProgress();
        progress.status(quest.id().toString(), QuestStatus.AVAILABLE);
        assertFalse(queries.repeatReady(quest, progress, Long.MAX_VALUE));
        progress.status(quest.id().toString(), QuestStatus.REWARD_CLAIMED);
        assertTrue(queries.repeatReady(quest, progress, 0));
        assertFalse(queries.repeatReady(quest(List.of(), false, false), progress, Long.MAX_VALUE));
    }

    private static QuestDefinition quest(List<RewardDefinition> rewards, boolean repeatable, boolean ignoreRewards) {
        var behavior = new QuestBehavior(false, false, false, 0, false, false, false,
                DependencyRequirement.ALL_COMPLETED, 0, false, repeatable, 0, ignoreRewards);
        return new QuestDefinition(id("book"), id("quest"), id("chapter"), "Quest", "", "", "", 0, 0,
                List.of(), List.of(), rewards, "", QuestAppearance.DEFAULT, behavior, Map.of());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("query", path); }
}

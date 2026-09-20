package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
/** Client-only dispatch keeps type-specific response pages out of the common network envelope. */
public final class ClientRewardInteractions {
    private ClientRewardInteractions() {}
    public static void prepareClaim(String revision, String rewardId) {
        ClientQuestState.get().book().ifPresent(snapshot -> snapshot.book().quests().stream()
                .flatMap(quest -> quest.rewards().stream()).filter(reward -> reward.id().toString().equals(rewardId))
                .findFirst().ifPresent(reward -> ClientRewardPresentationRegistry.get(reward.typeId())
                        .prepareClaim(revision, ApiViews.reward(reward))));
    }
}

package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared task/reward projection used by typed editor sections without depending on the parent screen. */
enum QuestTypedEntryKind {
    TASK("TASK", "task", "screen.brnquest.editor.typed.tasks_heading"),
    REWARD("REWARD", "reward", "screen.brnquest.editor.typed.rewards_heading");

    record Value(ResourceLocation id, ResourceLocation typeId) {}

    record Entry(ResourceLocation id, ResourceLocation typeId, Map<String, String> config,
                 boolean optional, String claimPolicy, boolean teamReward,
                 TaskDefinition task, RewardDefinition reward) {
        static Entry task(TaskDefinition task) {
            return new Entry(task.id(), task.typeId(), task.config(), task.optional(), "", false, task, null);
        }

        static Entry reward(RewardDefinition reward) {
            return new Entry(reward.id(), reward.typeId(), reward.config(), false,
                    reward.claimPolicy(), reward.teamReward(), null, reward);
        }
    }

    private final String actionPrefix;
    private final String idStem;
    private final String headingKey;

    QuestTypedEntryKind(String actionPrefix, String idStem, String headingKey) {
        this.actionPrefix = actionPrefix;
        this.idStem = idStem;
        this.headingKey = headingKey;
    }

    String actionPrefix() { return actionPrefix; }
    String idStem() { return idStem; }
    String headingKey() { return headingKey; }

    int size(QuestDefinition quest) {
        return this == TASK ? quest.tasks().size() : quest.rewards().size();
    }

    Value value(QuestDefinition quest, int index) {
        if (this == TASK) {
            TaskDefinition task = quest.tasks().get(index);
            return new Value(task.id(), task.typeId());
        }
        RewardDefinition reward = quest.rewards().get(index);
        return new Value(reward.id(), reward.typeId());
    }

    Entry entry(QuestDefinition quest, ResourceLocation id) {
        if (this == TASK) {
            return quest.tasks().stream().filter(task -> task.id().equals(id))
                    .findFirst().map(Entry::task).orElse(null);
        }
        return quest.rewards().stream().filter(reward -> reward.id().equals(id))
                .findFirst().map(Entry::reward).orElse(null);
    }

    List<ResourceLocation> builtIns() {
        return this == TASK
                ? List.of(TaskTypes.CHECKMARK, TaskTypes.CUSTOM, TaskTypes.ITEM, TaskTypes.XP)
                : List.of(RewardTypes.CUSTOM, RewardTypes.ITEM, RewardTypes.XP);
    }

    Set<ResourceLocation> registeredTypes() {
        return this == TASK ? TaskTypeRegistry.registeredIds() : RewardTypeRegistry.registeredIds();
    }

    boolean known(ResourceLocation typeId) { return registeredTypes().contains(typeId); }

    boolean addable(ResourceLocation typeId) {
        if (this == TASK) {
            var type = TaskTypeRegistry.get(typeId);
            return type != null && !type.hiddenFromCreation();
        }
        var type = RewardTypeRegistry.get(typeId);
        return type != null && !type.hiddenFromCreation();
    }

    boolean itemBacked(ResourceLocation typeId) { return this == REWARD && ClientConfigEditors.creation(true, typeId).isPresent(); }
    boolean choiceBacked(ResourceLocation typeId) { return this == TASK && ClientConfigEditors.creation(false, typeId).isPresent(); }
}

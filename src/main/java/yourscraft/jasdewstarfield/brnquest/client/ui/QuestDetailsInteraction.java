package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the rendered detail frame and converts pointer input into protocol-free semantic intents. */
final class QuestDetailsInteraction {
    enum Action {
        OPEN_UPSTREAM, OPEN_DOWNSTREAM,
        CLOSE, COMPLETE_QUEST, TOGGLE_TRACKED, SUBMIT_TASK, OPEN_SUBMISSION_CHOICES,
        OPEN_ITEM_SLOT_SELECTION, CLAIM_REWARD, OPEN_REWARD_OPTIONS, QUICK_EDIT_TEXT,
        EDIT_PROPERTIES, EDIT_TASKS, EDIT_REWARDS, EDIT_DEPENDENCIES
    }

    record Intent(Action action, ResourceLocation questId, ResourceLocation targetId, String textArea) {}
    record ClickResult(boolean consumed, Intent intent) {
        static ClickResult ignored() { return new ClickResult(false, null); }
        static ClickResult consumed(Intent intent) { return new ClickResult(true, intent); }
    }
    record TaskTarget(ResourceLocation taskId, UiRect action, UiRect candidates, Action rowAction) {}
    record Frame(QuestScreenFrameIdentity identity, ResourceLocation questId, boolean editing, boolean gameplay,
                 UiRect panel, UiRect close, UiRect complete, UiRect track,
                 Map<String, UiRect> textAreas, Map<Action, UiRect> editorActions,
                 List<TaskTarget> tasks, Map<ResourceLocation, UiRect> rewards,
                 Map<Action, UiRect> relationToggles) {
        Frame {
            textAreas = Map.copyOf(textAreas);
            editorActions = Map.copyOf(editorActions);
            tasks = List.copyOf(tasks);
            rewards = Map.copyOf(rewards);
            relationToggles = Map.copyOf(relationToggles);
        }
    }

    private QuestScreenFrameIdentity identity;
    private ResourceLocation questId;
    private boolean editing;
    private boolean gameplay;
    private UiRect panel;
    private UiRect close;
    private UiRect complete;
    private UiRect track;
    private final Map<String, UiRect> textAreas = new LinkedHashMap<>();
    private final Map<Action, UiRect> editorActions = new LinkedHashMap<>();
    private final List<TaskTarget> tasks = new ArrayList<>();
    private final Map<ResourceLocation, UiRect> rewards = new LinkedHashMap<>();
    private Frame frame;
    private final Map<Action, UiRect> relationToggles = new LinkedHashMap<>();
    private final Map<ResourceLocation,UiRect> rewardOptions = new LinkedHashMap<>();

    void begin(QuestScreenFrameIdentity identity, ResourceLocation questId, boolean editing, boolean gameplay,
               UiRect panel, UiRect close) {
        this.identity = identity;
        this.questId = questId;
        this.editing = editing;
        this.gameplay = gameplay;
        this.panel = panel;
        this.close = close;
        complete = null;
        track = null;
        textAreas.clear();
        editorActions.clear();
        tasks.clear();
        rewards.clear();
        rewardOptions.clear();
        relationToggles.clear();
        frame = null;
    }

    void statusActions(UiRect complete, UiRect track) {
        this.complete = complete;
        this.track = track;
    }

    void textAreas(Map<String, UiRect> areas) {
        textAreas.putAll(areas);
    }

    void editorAction(Action action, UiRect bounds) {
        if (action != null && bounds != null) editorActions.put(action, bounds);
    }

    void task(ResourceLocation taskId, UiRect action, UiRect candidates, Action rowAction) {
        tasks.add(new TaskTarget(taskId, action, candidates, rowAction));
    }

    void reward(ResourceLocation rewardId, UiRect bounds) {
        if (rewardId != null && bounds != null) rewards.put(rewardId, bounds);
    }

    /** Read-only candidates remain clickable even when a reward cannot be claimed. */
    void rewardOptions(ResourceLocation id, UiRect bounds) { if (bounds != null) rewardOptions.put(id,bounds); }

    /** Navigation is read-only and remains available in preview, independently of gameplay actions. */
    void relationToggle(Action action, UiRect bounds) {
        if (bounds != null) relationToggles.put(action, bounds);
    }

    Frame finish() {
        frame = new Frame(identity, questId, editing, gameplay, panel, close, complete, track,
                textAreas, editorActions, tasks, rewards, relationToggles);
        return frame;
    }

    void invalidate() {
        frame = null;
    }

    ClickResult click(QuestScreenFrameIdentity current, double x, double y, int button) {
        if (!accepts(current) || !frame.panel().contains(x, y)) return ClickResult.ignored();
        if (frame.close() != null && frame.close().contains(x, y)) return intent(Action.CLOSE, null, null);
        if (frame.editing() && button == 1) {
            for (Map.Entry<String, UiRect> entry : frame.textAreas().entrySet()) {
                if (entry.getValue().contains(x, y)) return intent(Action.QUICK_EDIT_TEXT, null, entry.getKey());
            }
        }
        if (frame.editing()) {
            for (Map.Entry<Action, UiRect> entry : frame.editorActions().entrySet()) {
                if (entry.getValue().contains(x, y)) return intent(entry.getKey(), null, null);
            }
        }
        // Right click is reserved for author gestures and never becomes a gameplay mutation.
        if (button != 0) return intent(null, null, null);
        for (var entry : frame.relationToggles().entrySet())
            if (entry.getValue().containsExclusive(x, y)) return intent(entry.getKey(), null, null);
        for (TaskTarget task : frame.tasks()) {
            if (task.candidates() != null && task.candidates().contains(x, y)) {
                return intent(Action.OPEN_SUBMISSION_CHOICES, task.taskId(), null);
            }
        }
        for (var entry : rewardOptions.entrySet())
            if (entry.getValue().contains(x,y)) return intent(Action.OPEN_REWARD_OPTIONS,entry.getKey(),null);
        // Candidate explanations stay readable in preview/locked states; mutations do not.
        if (!frame.gameplay()) return intent(null, null, null);
        if (frame.complete() != null && frame.complete().contains(x, y)) {
            return intent(Action.COMPLETE_QUEST, null, null);
        }
        if (frame.track() != null && frame.track().contains(x, y)) {
            return intent(Action.TOGGLE_TRACKED, null, null);
        }
        for (TaskTarget task : frame.tasks()) {
            if (task.action() != null && task.action().contains(x, y)) {
                return intent(task.rowAction(), task.taskId(), null);
            }
        }
        for (Map.Entry<ResourceLocation, UiRect> reward : frame.rewards().entrySet()) {
            if (reward.getValue().contains(x, y)) return intent(Action.CLAIM_REWARD, reward.getKey(), null);
        }
        return intent(null, null, null);
    }

    private ClickResult intent(Action action, ResourceLocation targetId, String textArea) {
        return ClickResult.consumed(action == null ? null : new Intent(action, frame.questId(), targetId, textArea));
    }

    private boolean accepts(QuestScreenFrameIdentity current) {
        return frame != null && frame.identity().equals(current);
    }
}

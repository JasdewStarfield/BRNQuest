package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Coordinates live sampling and deferred baseline preparation inside the engine owner transaction. */
final class ProgressSampling {
    interface Host {
        OperationResult complete(ServerPlayer player, ResourceLocation questId);
        long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress, long amount);
    }
    private final ProgressQueries queries;
    private final ProgressEffects effects;
    private final Host host;
    private volatile TaskPollingPlan pollingPlan;
    private volatile InventoryTaskPlan inventoryTaskPlan;
    // Only the outermost polling pass drains this queue; sequential successors prepare before their event.
    private int pollingDepth;
    private final Set<ServerPlayer> pendingSamplePreparation = new LinkedHashSet<>();

    ProgressSampling(ProgressQueries queries, ProgressEffects effects, Host host) {
        this.queries = queries; this.effects = effects; this.host = host;
    }

    private InventoryTaskPlan inventoryTaskPlan(yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot) {
        InventoryTaskPlan plan = inventoryTaskPlan;
        if (plan == null || plan.snapshot != snapshot) {
            plan = new InventoryTaskPlan(snapshot);
            inventoryTaskPlan = plan;
        }
        return plan;
    }

    private TaskPollingPlan pollingPlan(yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot) {
        TaskPollingPlan plan = pollingPlan;
        if (plan == null || plan.snapshot != snapshot) {
            plan = new TaskPollingPlan(snapshot);
            pollingPlan = plan;
        }
        return plan;
    }

    private static final class InventoryTaskPlan {
        private final yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot;
        private final List<QuestDefinition> quests;

        private InventoryTaskPlan(yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot) {
            this.snapshot = snapshot;
            this.quests = snapshot.book().quests().stream().filter(quest -> quest.tasks().stream().anyMatch(task -> {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                return type != null && TaskTypeExecutor.reevaluateOnInventoryChange(type, ApiViews.task(task));
            })).toList();
        }
    }

    void pollTasks(ServerPlayer player) {
        pollingDepth++;
        try {
            var snapshot = QuestBookManager.get().active().orElse(null);
            if (snapshot == null) return;
            prepareSamples(player, snapshot, queries.progress(player), new yourscraft.jasdewstarfield.brnquest.task.TaskSampleValues());
            var due = pollingPlan(snapshot).dueQuests(player.server.getTickCount(), id -> {
                var type = TaskTypeRegistry.get(id);
                return type == null ? 0 : type.pollingIntervalTicks();
            });
            if (due.isEmpty()) return;
            var progress = queries.progress(player);
            boolean team = queries.shared(player);
            var values = new yourscraft.jasdewstarfield.brnquest.task.TaskSampleValues();
            for (var quest : due) {
                var status = progress.status(quest.id().toString());
                if ((status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) || !queries.dependenciesComplete(quest, progress)) continue;
                boolean changed = false;
                for (var task : quest.tasks()) {
                    var type = TaskTypeRegistry.get(task.typeId());
                    if (type == null || type.pollingIntervalTicks() <= 0
                            || player.server.getTickCount() % Math.max(1, type.pollingIntervalTicks()) != 0
                            || !queries.taskIsCurrent(player, quest, task, progress)) continue;
                    var context = queries.taskContext(player, quest, task, progress);
                    if (type.usesSamplingState() && !progress.samplePrepared(task.id().toString(), player.getUUID())) {
                        var prepared = TaskTypeExecutor.prepareSample(type, new yourscraft.jasdewstarfield.brnquest.task.TaskSamplingContext(
                                context, progress.sampleState(task.id().toString(), player.getUUID()), team, values));
                        if (progress.sampleState(task.id().toString(), player.getUUID(), prepared.state())) QuestProgressData.get(player.server).setDirty();
                        if (progress.samplePrepared(task.id().toString(), player.getUUID(), prepared.ready())) QuestProgressData.get(player.server).setDirty();
                    }
                    var sampleContext = new yourscraft.jasdewstarfield.brnquest.task.TaskSamplingContext(context,
                            progress.sampleState(task.id().toString(), player.getUUID()), team, values);
                    var result = TaskTypeExecutor.sample(type, sampleContext);
                    if (type.usesSamplingState() && progress.sampleState(task.id().toString(), player.getUUID(), result.state()))
                        QuestProgressData.get(player.server).setDirty();
                    long next = result.progress();
                    if (next > context.progress()) {
                        host.changeTaskProgress(player, quest, task, progress, next - context.progress()); changed = true;
                    }
                }
                if (changed) {
                    QuestProgressData.get(player.server).setDirty();
                    host.complete(player, quest.id());
                    effects.sync(player, true);
                }
            }
            return;
        } finally {
            if (--pollingDepth == 0) {
                var pending = List.copyOf(pendingSamplePreparation);
                pendingSamplePreparation.clear();
                pending.forEach(this::prepareOwnerSamples);
            }
        }

    }

    void prepareOwnerSamples(ServerPlayer actor) {
        if (pollingDepth > 0) { pendingSamplePreparation.add(actor); return; }
        prepareOwnerSamplesNow(actor);
    }

    void prepareOwnerSamplesNow(ServerPlayer actor) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        var owner = ProgressOwnerService.require(actor);
        var members = new LinkedHashSet<ServerPlayer>();
        members.add(actor);
        for (var online : actor.server.getPlayerList().getPlayers())
            if (online != actor && ProgressOwnerService.resolve(online).map(view -> view.id().equals(owner)).orElse(false)) members.add(online);
        for (var member : members) prepareSamples(member, snapshot, queries.progress(member), new yourscraft.jasdewstarfield.brnquest.task.TaskSampleValues());
    }

    void prepareSamples(ServerPlayer player, yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot,
                                PlayerProgress progress, yourscraft.jasdewstarfield.brnquest.task.TaskSampleValues values) {
        var quests = pollingPlan(snapshot).statefulQuests(id -> {
            var type = TaskTypeRegistry.get(id);
            return type != null && type.usesSamplingState();
        });
        prepareSamples(player, snapshot, progress, values, quests);
    }

    void prepareSamples(ServerPlayer player, yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot,
                                PlayerProgress progress, yourscraft.jasdewstarfield.brnquest.task.TaskSampleValues values,
                                List<QuestDefinition> quests) {
        boolean team = queries.shared(player);
        QuestProgressData.get(player.server).activateSamplingOwner(player.getUUID(), ProgressOwnerService.require(player));
        for (var quest : quests) {
            var status = progress.status(quest.id().toString());
            // Completed history keeps its receipt; identity changes are reconciled while unfinished, even if locked.
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) continue;
            if (queries.allMembers(player, quest) && progress.objectives(player.getUUID()).status(quest.id().toString()) == QuestStatus.COMPLETED
                    && quest.tasks().stream().filter(task -> !task.optional()).anyMatch(task -> {
                        var type = TaskTypeRegistry.get(task.typeId());
                        return type == null || !TaskTypeExecutor.satisfied(type, queries.taskContext(player, quest, task, progress));
                    })) {
                // A higher threshold or an added required objective reopens this member's unfinished quest.
                progress.objectives(player.getUUID()).status(quest.id().toString(), QuestStatus.AVAILABLE);
                QuestProgressData.get(player.server).setDirty();
            }
            if (status == QuestStatus.LOCKED && queries.dependenciesComplete(quest, progress)) {
                status = QuestStatus.AVAILABLE;
                progress.status(quest.id().toString(), status);
                QuestProgressData.get(player.server).setDirty();
            }
            boolean eligible = (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && queries.dependenciesComplete(quest, progress);
            boolean memberLedger = team && quest.behavior().requireAllTeamMembers();
            for (var task : quest.tasks()) {
                var type = TaskTypeRegistry.get(task.typeId());
                if (type == null || !type.usesSamplingState()) continue;
                var info = pollingPlan(snapshot).samplingInfo(task, type);
                String identity = team && quest.behavior().requireAllTeamMembers() ? info.memberIdentity() : info.ownerIdentity();
                if (identity.isEmpty()) continue;
                if (progress.prepareSamplingIdentity(quest.id().toString(), info.taskId(), identity))
                    QuestProgressData.get(player.server).setDirty();
                boolean resuming = progress.sampleSuspended(info.taskId(), player.getUUID());
                boolean memberComplete = memberLedger && progress.objectives(player.getUUID()).status(quest.id().toString()) == QuestStatus.COMPLETED;
                boolean current = eligible && !memberComplete
                        && (!quest.behavior().sequentialTasks() || task.optional() || queries.taskIsCurrent(player, quest, task, progress));
                if (!current) {
                    // Relocked dependencies or a reset predecessor pause the read position until eligibility returns.
                    if (!memberComplete && progress.samplePrepared(info.taskId(), player.getUUID())
                            && progress.suspendSample(info.taskId(), player.getUUID())) QuestProgressData.get(player.server).setDirty();
                    continue;
                }
                if (progress.samplePrepared(info.taskId(), player.getUUID()) && !resuming) continue;
                var context = new TaskContext(player, quest.bookId(), quest.id(), info.view(), queries.taskLedger(player, quest, progress).taskProgress(info.taskId()));
                var before = progress.sampleState(info.taskId(), player.getUUID());
                var prepared = TaskTypeExecutor.prepareSample(type,
                        new yourscraft.jasdewstarfield.brnquest.task.TaskSamplingContext(context, before, team, values, resuming));
                if (progress.sampleState(task.id().toString(), player.getUUID(), prepared.state())) QuestProgressData.get(player.server).setDirty();
                if (progress.samplePrepared(info.taskId(), player.getUUID(), prepared.ready())) QuestProgressData.get(player.server).setDirty();
                if (resuming && prepared.ready()) { progress.resumeSample(task.id().toString(), player.getUUID()); QuestProgressData.get(player.server).setDirty(); }
                // An initial state may discard legacy count when selecting a new baseline-based mode.
                if (before.values().isEmpty() && prepared.ready() && prepared.progress() != context.progress())
                    host.changeTaskProgress(player, quest, task, progress, prepared.progress() - context.progress());
            }
        }
    }

    void submitInventoryTasks(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        PlayerProgress progress = queries.progress(player);
        boolean changed = false;
        for (QuestDefinition quest : inventoryTaskPlan(snapshot).quests) {
            QuestStatus status = progress.status(quest.id().toString());
            if ((status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE)
                    || !queries.dependenciesComplete(quest, progress)) continue;
            boolean questChanged = false;
            for (TaskDefinition task : quest.tasks()) {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                if (type == null || !TaskTypeExecutor.reevaluateOnInventoryChange(type, ApiViews.task(task))
                        || queries.taskLedger(player, quest, progress).taskProgress(task.id().toString()) >= 1
                        || !queries.taskIsCurrent(player, quest, task, progress)) continue;
                // The task validates the live stack and component policy on the server.
                // A rejected sample leaves both inventory and the receipt ledger untouched.
                try {
                    if (!TaskTypeExecutor.submit(type, queries.taskContext(player, quest, task, progress)).success()) continue;
                } catch (RuntimeException failure) {
                    continue;
                }
                host.changeTaskProgress(player, quest, task, progress, 1);
                questChanged = true;
            }
            if (questChanged) {
                QuestProgressData.get(player.getServer()).setDirty();
                host.complete(player, quest.id());
                changed = true;
            }
        }
        if (changed) effects.sync(player, true);
        return;

    }

    void recordCraft(ServerPlayer player, ItemStack crafted) {
        if (crafted.isEmpty()) return;
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        PlayerProgress progress = queries.progress(player);
        for (QuestDefinition quest : snapshot.book().quests()) {
            QuestStatus status = progress.status(quest.id().toString());
            if ((status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE)
                    || !queries.dependenciesComplete(quest, progress)) continue;
            for (TaskDefinition task : quest.tasks()) {
                var type = TaskTypeRegistry.get(task.typeId());
                if (type == null || !queries.taskIsCurrent(player, quest, task, progress)) continue;
                var context = queries.taskContext(player, quest, task, progress);
                long sampled;
                try {
                    sampled = TaskTypeExecutor.craftedProgress(type, context, crafted);
                } catch (RuntimeException failure) {
                    // Ignore a broken observer; no progress has been written and other types still receive the event.
                    continue;
                }
                long previous = queries.taskLedger(player, quest, progress).taskProgress(task.id().toString());
                if (sampled <= previous) continue;
                host.changeTaskProgress(player, quest, task, progress, sampled - previous);
                if (TaskTypeExecutor.satisfied(type, queries.taskContext(player, quest, task, progress))) {
                    host.complete(player, quest.id());
                    break;
                }
            }
        }
        QuestProgressData.get(player.getServer()).setDirty();
        effects.sync(player, true);
        return;

    }

    void resetTransientStates(ServerPlayer player, QuestDefinition quest, List<TaskDefinition> tasks) {
        var owner = ProgressOwnerService.require(player);
        var players = new java.util.LinkedHashSet<ServerPlayer>();
        players.add(player);
        for (var online : player.getServer().getPlayerList().getPlayers()) {
            if (ProgressOwnerService.resolve(online).map(view -> view.id().equals(owner)).orElse(false)) players.add(online);
        }
        for (var member : players) for (var task : tasks) {
            var type = TaskTypeRegistry.get(task.typeId());
            if (type != null) type.resetTransientState(queries.taskContext(member, quest, task, queries.progress(member)));
        }
    }

    List<QuestDefinition> quests(yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot snapshot) {
        return pollingPlan(snapshot).quests;
    }

    /** Initialize sequential successors before observers run, then batch remaining owner preparation. */
    void progressChanged(ServerPlayer player, QuestDefinition quest, PlayerProgress progress) {
        if (pollingDepth > 0 && quest.behavior().sequentialTasks()) {
            var snapshot = QuestBookManager.get().active().orElseThrow();
            prepareSamples(player, snapshot, progress, new yourscraft.jasdewstarfield.brnquest.task.TaskSampleValues(), List.of(quest));
        }
        prepareOwnerSamples(player);
    }
}

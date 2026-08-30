package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.network.AdminProgressNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressService;

import java.util.*;

/** Child management surface: runtime progress never becomes an editor draft mutation. */
public final class AdminProgressScreen extends Screen {
    private enum Page { PLAYERS, DETAIL, CONFIRM, TECHNICAL }
    private final Screen parent;
    private final String bookId, revision, questId, taskId;
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private EditorTextField search;
    private Page page = Page.PLAYERS, technicalParent = Page.DETAIL;
    private List<AdminProgressService.PlayerEntry> players = List.of();
    private AdminProgressService.PlayerEntry selected;
    private AdminProgressService.View view;
    private AdminProgressAction action;
    private String requestId = "", requestMode = "", token = "", lastFilter = "", code = "";
    private Component message;
    private boolean busy, initialized, failed;
    private long requestStarted, previousFrame, searchChanged;
    private int contentHeight;
    private boolean quickSelf;

    public AdminProgressScreen(Screen parent, String bookId, String revision, String questId, String taskId) {
        super(text("title"));
        this.parent = parent;
        this.bookId = bookId;
        this.revision = revision;
        this.questId = questId;
        this.taskId = taskId;
        action = taskId.isEmpty() ? AdminProgressAction.FORCE_QUEST : AdminProgressAction.FORCE_TASK;
    }

    public AdminProgressScreen(Screen parent, String bookId, String revision, String questId, String taskId,
                               AdminProgressAction selfAction) {
        this(parent, bookId, revision, questId, taskId);
        quickSelf = true;
        action = selfAction;
    }

    @Override protected void init() {
        if (search == null) {
            search = new EditorTextField(font, text("search"), 64);
            search.setHint(text("search"));
            search.setResponder(value -> searchChanged = System.nanoTime());
        }
        // Explicit foreground rendering keeps EditBox above the parent and the modal panel.
        addWidget(search);
        if (!initialized) {
            initialized = true;
            if (quickSelf && minecraft.player != null) {
                selected = new AdminProgressService.PlayerEntry(minecraft.player.getUUID().toString(), minecraft.player.getScoreboardName());
                page = Page.DETAIL;
                request("PREVIEW");
            } else request("CATALOG");
        }
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        parent.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override public void tick() {
        parent.tick();
        super.tick();
        if (busy && System.nanoTime() - requestStarted > 15_000_000_000L) {
            busy = false;
            failed = true;
            message = text("timeout");
            // COMMIT retries retain the exact same server ticket; never manufacture a second action.
        }
        if (page == Page.PLAYERS && !busy && !search.getValue().equals(lastFilter)
                && System.nanoTime() - searchChanged > 250_000_000L) request("CATALOG");
    }

    private void request(String mode) {
        busy = true;
        failed = false;
        message = text("waiting");
        requestId = UUID.randomUUID().toString();
        requestMode = mode;
        requestStarted = System.nanoTime();
        if (mode.equals("CATALOG")) lastFilter = search.getValue();
        var intent = selected == null ? null : new AdminProgressService.Intent(
                selected.id(), bookId, revision, questId, taskId, action);
        AdminProgressNetwork.send(new AdminProgressNetwork.Request(requestId, mode, intent, token, lastFilter));
    }

    /** A reply only belongs to the exact outstanding request, not a previously closed screen. */
    public void receive(AdminProgressNetwork.Response response) {
        if (response == null || !requestId.equals(response.requestId()) || response.reply() == null) return;
        var reply = response.reply();
        busy = false;
        failed = !reply.result().success();
        code = reply.result().code();
        String errorKey = net.minecraft.client.resources.language.I18n.exists("screen.brnquest.admin.error." + code)
                ? "error." + code : "error.UNKNOWN";
        message = failed ? text(errorKey) : text(requestMode.equals("COMMIT") ? "applied" : "runtime_hint");
        if (failed) {
            if (requestMode.equals("COMMIT")) {
                token = "";
                page = Page.DETAIL;
            }
            return;
        }
        if (requestMode.equals("CATALOG")) players = List.copyOf(reply.players());
        if (reply.view() != null) view = reply.view();
        if (requestMode.equals("PREVIEW")) {
            token = reply.token();
            // A deliberate self-complete menu action needs no extra click; reset still requires confirmation.
            if (quickSelf && !action.reset()) { request("COMMIT"); return; }
            page = Page.CONFIRM;
            scroll.snap(0);
        } else if (requestMode.equals("COMMIT")) {
            token = "";
            if (quickSelf) { minecraft.setScreen(parent); return; }
            page = Page.DETAIL;
            scroll.snap(0);
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, width, height, 0x70151820);
        UiRect panel = panel(), viewport = viewport();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xFF202632);
        graphics.drawCenteredString(font, quickSelf ? text(action.reset() ? "self_reset" : "self_force") : title,
                panel.centerX(), panel.top() + 8, 0xFFFFFFFF);
        if (page == Page.PLAYERS) {
            search.show(new UiRect(panel.left() + 9, panel.top() + 27, panel.right() - 9, panel.top() + 45), true);
            search.render(graphics, mouseX, mouseY, partialTick);
        } else search.hide();
        List<FormattedCharSequence> lines = bodyLines(viewport.width() - 8);
        contentHeight = page == Page.PLAYERS ? players.size() * 22 : lines.size() * 12;
        long now = System.nanoTime();
        double elapsed = previousFrame == 0 ? 1.0 / 60 : Math.min(0.1, (now - previousFrame) / 1_000_000_000.0);
        previousFrame = now;
        double offset = scroll.frameAndRender(graphics, viewport.right() + 2, viewport.top(), viewport.bottom(),
                contentHeight, viewport.height(), elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        if (page == Page.PLAYERS) {
            for (int index = 0; index < players.size(); index++) {
                int y = viewport.top() + index * 22 - (int) Math.round(offset);
                UiRect row = new UiRect(viewport.left(), y, viewport.right(), y + 21);
                if (row.bottom() <= viewport.top() || row.top() >= viewport.bottom()) continue;
                EditorButton.renderInteractive(graphics, font, row, EditorButton.Definition.text(
                        Component.literal(players.get(index).name()), null), !busy, false,
                        EditorButton.Tone.PRIMARY, viewport.contains(mouseX, mouseY) ? mouseX : -1, mouseY);
            }
            if (players.isEmpty() && !busy) graphics.drawString(font, text("no_players"), viewport.left() + 4,
                    viewport.top() + 5, 0xFF9FB0C2, false);
        } else {
            for (int index = 0; index < lines.size(); index++) {
                graphics.drawString(font, lines.get(index), viewport.left() + 4,
                        viewport.top() + index * 12 - (int) Math.round(offset), 0xFFE0E6EE, false);
            }
        }
        graphics.disableScissor();
        if (message != null) {
            var statusLines = font.split(message, panel.width() - 18);
            for (int index = 0; index < Math.min(2, statusLines.size()); index++) graphics.drawString(font,
                    statusLines.get(index), panel.left() + 9, panel.bottom() - 51 + index * 10,
                    failed ? 0xFFFF9393 : 0xFF9FB0C2, false);
        }
        List<String> labels = buttons();
        for (int index = 0; index < labels.size(); index++) {
            String label = labels.get(index);
            boolean enabled = !busy && (!label.equals("force") && !label.equals("reset") || view != null);
            if (label.equals("back")) enabled = true;
            EditorButton.renderInteractive(graphics, font, buttonBounds(index, labels.size()),
                    EditorButton.Definition.text(text(label), null), enabled, false,
                    label.equals("reset") || label.equals("confirm") && action.reset() ? EditorButton.Tone.DANGER
                            : label.equals("force") || label.equals("confirm") ? EditorButton.Tone.WARNING
                            : EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private List<FormattedCharSequence> bodyLines(int availableWidth) {
        List<Component> body = new ArrayList<>();
        if (page == Page.PLAYERS) return List.of();
        if (quickSelf && page != Page.TECHNICAL) {
            // Self actions need only the affected title and consequences, not a second player-management page.
            if (view != null) body.add(objectTitle());
            if (action.reset() && view != null) {
                body.add(text("no_reclaim"));
                body.add(text(view.clearsRewardClaims() ? "clears_claims" : "keeps_claims"));
                body.add(text("may_recomplete"));
            }
            return body.stream().flatMap(line -> font.split(line, availableWidth).stream()).toList();
        }
        if (selected != null) body.add(text("player", selected.name()));
        if (view != null) {
            body.add(objectTitle());
            body.add(text("state", Component.translatable("screen.brnquest.status." + view.status().toLowerCase(Locale.ROOT))));
            if (!taskId.isEmpty()) body.add(text(view.taskProgress() >= 1 ? "task_done" : "task_pending"));
            body.add(text("counts", view.completedTasks(), view.totalTasks(), view.claimedRewards(), view.totalRewards()));
        }
        if (page == Page.CONFIRM && view != null) {
            body.add(text("action." + action.name()));
            if (action.reset()) {
                body.add(text("no_reclaim"));
                body.add(text("no_cascade"));
                body.add(text("may_recomplete"));
                body.add(text(view.clearsRewardClaims() ? "clears_claims" : "keeps_claims"));
            } else {
                body.add(text("no_consumption"));
                if (view.completesQuest() && view.automaticRewards() > 0) body.add(text("auto_rewards", view.automaticRewards()));
            }
            body.add(text("confirm_hint"));
        }
        if (page == Page.TECHNICAL) {
            body.add(Component.literal("Player: " + (selected == null ? "" : selected.id())));
            body.add(Component.literal("Quest: " + questId));
            if (!taskId.isEmpty()) body.add(Component.literal("Task: " + taskId));
            body.add(Component.literal("Book: " + bookId));
            body.add(Component.literal("Active revision: " + revision));
            if (view != null) body.add(Component.literal("Owner: " + view.owner()));
            body.add(Component.literal("Result: " + code));
        }
        return body.stream().flatMap(line -> font.split(line, availableWidth).stream()).toList();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        List<String> labels = buttons();
        for (int index = 0; index < labels.size(); index++) {
            if (!buttonBounds(index, labels.size()).contains(mouseX, mouseY)) continue;
            String label = labels.get(index);
            if (label.equals("back")) { back(); return true; }
            if (busy) return true;
            switch (label) {
                case "technical" -> { technicalParent = page; page = Page.TECHNICAL; scroll.snap(0); }
                case "refresh" -> request("CATALOG");
                case "force", "reset" -> {
                    if (view == null) return true;
                    action = taskId.isEmpty() ? (label.equals("force") ? AdminProgressAction.FORCE_QUEST : AdminProgressAction.RESET_QUEST)
                            : (label.equals("force") ? AdminProgressAction.FORCE_TASK : AdminProgressAction.RESET_TASK);
                    request("PREVIEW");
                }
                case "confirm" -> { if (!token.isEmpty()) request("COMMIT"); }
                default -> {}
            }
            return true;
        }
        UiRect viewport = viewport();
        if (!busy && scroll.handleTrackClick(mouseX, mouseY, viewport.right() + 2, viewport.top(), viewport.bottom(),
                contentHeight, viewport.height())) return true;
        if (!busy && page == Page.PLAYERS && viewport.contains(mouseX, mouseY)) {
            int index = scroll.rowAt(mouseY, viewport.top(), viewport.bottom(), 22, players.size());
            if (index >= 0) {
                selected = players.get(index);
                page = Page.DETAIL;
                view = null;
                scroll.snap(0);
                search.hide();
                setFocused(null);
                request("INSPECT");
            }
            return true;
        }
        return page == Page.PLAYERS && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        UiRect viewport = viewport();
        if (viewport.contains(x, y)) {
            scroll.scrollWheel(vertical, BrnQuestClientConfig.VALUES.scrollStep.get(), contentHeight, viewport.height());
            return true;
        }
        return false;
    }

    @Override public void onClose() { back(); }
    private void back() {
        if (quickSelf && page != Page.TECHNICAL) {
            if (minecraft != null) minecraft.setScreen(parent);
            return;
        }
        if (busy || page == Page.PLAYERS) {
            if (minecraft != null) minecraft.setScreen(parent);
            return;
        }
        if (page == Page.TECHNICAL) page = technicalParent;
        else if (page == Page.CONFIRM) { token = ""; page = Page.DETAIL; }
        else { page = Page.PLAYERS; request("CATALOG"); }
        scroll.snap(0);
    }

    private List<String> buttons() {
        if (quickSelf && page == Page.CONFIRM) return List.of("back", "confirm");
        if (quickSelf && busy) return List.of("back");
        return switch (page) {
            case PLAYERS -> List.of("back", "refresh");
            case DETAIL -> List.of("back", "technical", "force", "reset");
            case CONFIRM -> List.of("back", "technical", "confirm");
            case TECHNICAL -> List.of("back");
        };
    }

    /** The server supplies the published title; an unset task title uses its localized client type name. */
    private Component objectTitle() {
        if (!taskId.isEmpty()) {
            var task = yourscraft.jasdewstarfield.brnquest.client.ClientQuestState.get().book().stream()
                    .flatMap(snapshot -> snapshot.book().quests().stream())
                    .filter(quest -> quest.id().toString().equals(questId))
                    .flatMap(quest -> quest.tasks().stream())
                    .filter(value -> value.id().toString().equals(taskId)).findFirst().orElse(null);
            if (task != null && view.title().equals(task.typeId().toString())) {
                return ClientTaskPresentationRegistry.get(task.typeId()).typeName(
                        yourscraft.jasdewstarfield.brnquest.api.ApiViews.task(task));
            }
        }
        return Component.literal(view.title());
    }
    private UiRect panel() {
        int w = Math.min(340, width - 12), h = Math.min(quickSelf && page != Page.TECHNICAL ? 190 : 270, height - 12);
        return new UiRect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2);
    }
    private UiRect viewport() {
        UiRect panel = panel();
        return new UiRect(panel.left() + 9, panel.top() + (page == Page.PLAYERS ? 50 : 28),
                panel.right() - 13, Math.max(panel.top() + 52, panel.bottom() - 58));
    }
    private UiRect buttonBounds(int index, int count) {
        UiRect panel = panel();
        int w = (panel.width() - 18 - (count - 1) * 4) / count;
        int left = panel.left() + 9 + index * (w + 4);
        return new UiRect(left, panel.bottom() - 26, left + w, panel.bottom() - 6);
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("screen.brnquest.admin." + key, args);
    }
}

package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

import static yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.*;

/** Untrusted wire input stops here: no players, services, side effects or packet delivery. */
final class AuthoringRequestDecoder {
    private static final Gson GSON = new Gson();
    private AuthoringRequestDecoder() {}

    enum FailureStatus { INVALID_REQUEST, CONFLICT }
    record Failure(String code, String path, String message, String objectId, FailureStatus status) {
        Failure(String code, String path, String message) { this(code, path, message, ""); }
        Failure(String code, String path, String message, String objectId) {
            this(code, path, message, objectId, FailureStatus.INVALID_REQUEST);
        }
    }
    record Result<T>(T value, Failure failure) {
        boolean success() { return failure == null; }
    }
    record OpenRequest(ResourceLocation bookId, String expectedDraftRevision) {}
    record CurrentRequest(ResourceLocation bookId, String activeRevision, String draftRevision, boolean replaceDraft) {}
    record LeaseRequest(UUID sessionId, String draftRevision) {}
    enum RecoveryAction { ABANDON, REFRESH, SAVE_AS }
    record RecoveryRequest(UUID sessionId, ResourceLocation bookId, RecoveryAction action, ResourceLocation targetBookId) {}

    static Result<OpenRequest> open(String bookId, String expectedRevision) {
        return boundary("INVALID_BOOK_ID", "Invalid draft book ID", () -> new OpenRequest(
                id(bookId, "bookId"), text(expectedRevision, "draftRevision", 32767, true)));
    }

    static Result<CurrentRequest> current(OpenCurrentSessionPayload wire) {
        return boundary("ACTIVE_BOOK_CHANGED", "The displayed task book is no longer active on this server",
                () -> new CurrentRequest(id(wire.bookId(), "bookId"),
                        text(wire.activeRevision(), "activeRevision", 32767, false),
                        text(wire.draftRevision(), "draftRevision", 32767, true), wire.replaceDraft()));
    }

    static Result<LeaseRequest> lease(String sessionId, String revision) {
        return boundary("INVALID_SESSION_ID", "Invalid edit-session ID", () -> new LeaseRequest(
                uuid(sessionId), text(revision, "draftRevision", 32767, false)));
    }

    static Result<RecoveryRequest> recovery(String json) {
        return boundary("INVALID_RECOVERY_REQUEST", "Invalid conflict recovery request", () -> {
            var wire = json(json, RecoveryWire.class);
            UUID session = uuid(wire.sessionId());
            ResourceLocation book = id(wire.bookId(), "bookId");
            RecoveryAction action;
            try { action = RecoveryAction.valueOf(wire.action()); }
            catch (RuntimeException invalid) {
                throw invalid("INVALID_RECOVERY_ACTION", "action", "Unknown conflict recovery action");
            }
            ResourceLocation target = null;
            if (action == RecoveryAction.SAVE_AS) {
                try { target = id(wire.targetBookId(), "targetBookId"); }
                catch (RuntimeException invalid) {
                    throw invalid("INVALID_RECOVERY_ACTION", "targetBookId", "Unknown conflict recovery action");
                }
            }
            return new RecoveryRequest(session, book, action, target);
        });
    }

    /** Bounds both UTF-8 bytes and JSON shape before Gson can coerce a null/non-object envelope. */
    private static <T> T json(String json, Class<T> type) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_EDITOR_METADATA_BYTES)
            throw invalid(null, "", "Request exceeds the editor metadata limit");
        var tree = JsonParser.parseString(json);
        if (!tree.isJsonObject()) throw invalid(null, "", "Request must be a JSON object");
        checkShape(tree.getAsJsonObject(), type);
        return GSON.fromJson(tree, type);
    }

    private static ResourceLocation id(String raw, String path) {
        ResourceLocation parsed = raw == null || raw.length() > 32767 ? null : ResourceLocation.tryParse(raw);
        if (parsed == null) throw invalid(null, path, "A valid namespaced ID is required");
        return parsed;
    }

    private static UUID uuid(String raw) {
        try {
            // Reject UUID.fromString's abbreviated groups while retaining ordinary uppercase UUIDs.
            UUID value = UUID.fromString(raw);
            if (!value.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) {
            throw invalid(null, "sessionId", "Invalid edit-session ID");
        }
    }

    private static String text(String value, String path, int maximum, boolean allowEmpty) {
        if (value == null || value.length() > maximum || (!allowEmpty && value.isBlank()))
            throw invalid(null, path, "Missing or oversized " + path);
        return value;
    }

    private static InvalidInput invalid(String code, String path, String message) {
        return new InvalidInput(new Failure(code, path, message));
    }

    /** Carries a stable code and field path without coupling decoding to service statuses. */
    private static final class InvalidInput extends IllegalArgumentException {
        private final Failure failure;
        private InvalidInput(Failure failure) { super(failure.message()); this.failure = failure; }
    }

    private static <T> Result<T> boundary(String code, String message, Supplier<T> decode) {
        try { return new Result<>(decode.get(), null); }
        catch (InvalidInput invalid) {
            Failure failure = invalid.failure;
            return new Result<>(null, new Failure(failure.code() == null ? code : failure.code(), failure.path(),
                    failure.code() == null ? message : failure.message(), failure.objectId(), failure.status()));
        } catch (RuntimeException invalid) {
            return new Result<>(null, new Failure(code, "", message));
        }
    }
    record SessionRequest(UUID sessionId, ResourceLocation bookId, String draftRevision) {}

    static Result<SessionRequest> publication(String sessionId, String bookId, String revision, boolean publish) {
        return boundary(publish ? "INVALID_PUBLISH_REQUEST" : "INVALID_SAVE_REQUEST",
                publish ? "Incomplete publish request" : "Incomplete draft save request", () -> new SessionRequest(
                        uuid(sessionId), id(bookId, "bookId"), text(revision, "draftRevision", 32767, false)));
    }
    enum IconKind { ITEM, TEXTURE }
    record AppearanceRequest(String shape, double size, double iconScale, double minWidth) {}
    record BehaviorRequest(boolean hideUntilDependenciesVisible, boolean hideUntilDependenciesComplete,
                           boolean invisibleUntilComplete, int visibleAfterTasks, boolean hideDetailsUntilStartable,
                           boolean hideTextUntilComplete, boolean hideLockIcon,
                           yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement dependencyRequirement,
                           int minimumRequiredDependencies, boolean sequentialTasks, boolean repeatable,
                           int repeatCooldownSeconds, boolean ignoreRewardBlocking) {}
    record QuestRequest(UUID sessionId, ResourceLocation bookId, String draftRevision, ResourceLocation questId,
                        ResourceLocation replacementQuestId, String title, String subtitle, String description,
                        IconKind iconKind, ResourceLocation iconId, boolean preserveIcon, Double x, Double y,
                        AppearanceRequest appearance, BehaviorRequest behavior) {}

    static Result<QuestRequest> quest(String json) {
        return boundary("INVALID_QUEST_UPDATE", "Incomplete quest update request", () -> {
            QuestUpdateWire wire = json(json, QuestUpdateWire.class);
            UUID session = uuid(wire.sessionId());
            ResourceLocation book = id(wire.bookId(), "bookId");
            ResourceLocation quest = id(wire.questId(), "questId");
            ResourceLocation replacement = id(wire.replacementQuestId(), "replacementQuestId");
            String revision = text(wire.draftRevision(), "draftRevision", 32767, false);
            text(wire.title(), "title", 256, true);
            text(wire.subtitle(), "subtitle", 256, true);
            text(wire.description(), "description", 32768, true);
            text(wire.iconKind(), "iconKind", 32767, true);
            text(wire.iconValue(), "iconValue", 32767, true);
            if ((wire.x() == null) != (wire.y() == null)
                    || (wire.x() != null && (!Double.isFinite(wire.x()) || !Double.isFinite(wire.y()))))
                throw invalid("INVALID_QUEST_POSITION", "position", "Quest coordinates must be finite and supplied together");
            IconKind kind = null;
            ResourceLocation icon = null;
            if (!wire.preserveIcon()) {
                try { kind = IconKind.valueOf(wire.iconKind()); }
                catch (RuntimeException invalid) { throw invalid("INVALID_ICON_KIND", "iconKind", "Unknown quest icon kind"); }
                if (!wire.iconValue().isBlank()) {
                    try { icon = id(wire.iconValue(), "iconValue"); }
                    catch (RuntimeException invalid) {
                        throw invalid(kind == IconKind.ITEM ? "INVALID_ICON_ITEM" : "INVALID_ICON_TEXTURE", "iconValue",
                                kind == IconKind.ITEM ? "Icon item must be a registered item ID" : "Icon texture must be a ResourceLocation");
                    }
                }
            }
            AppearanceRequest appearance = null;
            if (wire.shape() != null || wire.size() != null || wire.iconScale() != null || wire.minWidth() != null) {
                if (wire.shape() == null || wire.size() == null || wire.iconScale() == null || wire.minWidth() == null
                        || wire.shape().isBlank() || wire.shape().length() > 32767 || !Double.isFinite(wire.size()) || wire.size() <= 0
                        || !Double.isFinite(wire.iconScale()) || wire.iconScale() <= 0
                        || !Double.isFinite(wire.minWidth()) || wire.minWidth() < 0)
                    throw invalid("INVALID_QUEST_APPEARANCE", "appearance", "Quest appearance values must be finite and positive");
                appearance = new AppearanceRequest(wire.shape(), wire.size(), wire.iconScale(), wire.minWidth());
            }
            BehaviorRequest behavior = null;
            if (wire.behavior() != null && !wire.behavior().isEmpty()) {
                try {
                    var values = boundedConfig(wire.behavior());
                    String requirement = values.getOrDefault("dependency_requirement", "all_completed");
                    var dependency = yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement.valueOf(
                            requirement.strip().toUpperCase(java.util.Locale.ROOT));
                    behavior = new BehaviorRequest(bool(values, "hide_until_dependencies_visible"),
                            bool(values, "hide_until_dependencies_complete"), bool(values, "invisible_until_complete"),
                            integer(values, "visible_after_tasks"), bool(values, "hide_details_until_startable"),
                            bool(values, "hide_text_until_complete"), bool(values, "hide_lock_icon"), dependency,
                            integer(values, "minimum_required_dependencies"), bool(values, "sequential_tasks"),
                            bool(values, "repeatable"), integer(values, "repeat_cooldown_seconds"), bool(values, "ignore_reward_blocking"));
                } catch (RuntimeException invalid) {
                    throw invalid("INVALID_QUEST_BEHAVIOR", "behavior", "Quest behavior contains an invalid number or enum");
                }
            }
            return new QuestRequest(session, book, revision, quest, replacement, wire.title(), wire.subtitle(),
                    wire.description(), kind, icon, wire.preserveIcon(), wire.x(), wire.y(), appearance, behavior);
        });
    }

    private static boolean bool(java.util.Map<String, String> values, String key) {
        String value = values.getOrDefault(key, "false");
        if ("true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0b".equalsIgnoreCase(value)) return false;
        throw invalid(null, "behavior." + key, "Invalid boolean");
    }

    private static int integer(java.util.Map<String, String> values, String key) {
        String value = values.getOrDefault(key, "0").strip();
        if (!value.matches("-?[0-9]+[bBsSlL]?")) throw invalid(null, "behavior." + key, "Invalid integer");
        return Integer.parseInt(value.replaceAll("[bBsSlL]$", ""));
    }

    /** Opaque extension entries remain byte-for-byte strings and are defensively copied. */
    static java.util.Map<String, String> boundedConfig(java.util.Map<String, String> config) {
        if (config == null || config.isEmpty()) return java.util.Map.of();
        if (config.size() > 64) throw invalid("INVALID_EDITOR_MUTATION", "config", "Typed config exceeds 64 fields");
        var bounded = new java.util.LinkedHashMap<String, String>();
        for (var entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 128 || value == null || value.length() > 65536)
                throw invalid("INVALID_EDITOR_MUTATION", "config", "Typed config contains an invalid field");
            bounded.put(key, value);
        }
        return java.util.Collections.unmodifiableMap(bounded);
    }
    /** Gson accepts scalar coercions by default; reject them before creating typed request records. */
    private static void checkShape(com.google.gson.JsonObject object, Class<?> type) {
        for (var component : type.getRecordComponents()) {
            var value = object.get(component.getName());
            if (value == null || value.isJsonNull()) continue; // Required fields are checked by the specific decoder.
            Class<?> field = component.getType();
            boolean scalar = field == String.class || field == boolean.class || field == Boolean.class
                    || field == int.class || field == double.class || field == Double.class;
            if (scalar) {
                if (!value.isJsonPrimitive()) throw invalid(null, component.getName(), "Invalid field type");
                var primitive = value.getAsJsonPrimitive();
                if ((field == String.class && !primitive.isString())
                        || ((field == boolean.class || field == Boolean.class) && !primitive.isBoolean())
                        || ((field == int.class || field == double.class || field == Double.class) && !primitive.isNumber()))
                    throw invalid(null, component.getName(), "Invalid field type");
                if (field == int.class) {
                    try { primitive.getAsBigDecimal().intValueExact(); }
                    catch (RuntimeException invalid) { throw invalid(null, component.getName(), "Invalid integer"); }
                }
            } else if (java.util.Map.class.isAssignableFrom(field)) {
                if (!value.isJsonObject()) throw invalid(null, component.getName(), "Expected string map");
                for (var entry : value.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                        throw invalid(null, component.getName() + "." + entry.getKey(), "Expected string config value");
                }
            } else if (java.util.List.class.isAssignableFrom(field)) {
                if (!value.isJsonArray()) throw invalid(null, component.getName(), "Expected position list");
                for (var entry : value.getAsJsonArray()) {
                    if (!entry.isJsonObject()) throw invalid(null, "positions", "Expected position object");
                    var position = entry.getAsJsonObject();
                    for (String required : java.util.List.of("questId", "x", "y"))
                        if (!position.has(required) || position.get(required).isJsonNull())
                            throw invalid(null, "positions." + required, "Incomplete position");
                    checkShape(position, PositionWire.class);
                }
            }
        }
    }
    record Position(double x, double y) {}
    record MutationRequest(UUID sessionId, ResourceLocation bookId, String draftRevision, AuthoringMutationAction action,
                           ResourceLocation targetId, ResourceLocation parentId, ResourceLocation sourceId,
                           String title, int targetIndex, double x, double y,
                           java.util.Map<ResourceLocation, Position> positions, java.util.Map<String, String> config,
                           String claimPolicy, String locale) {
        MutationRequest {
            positions = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(positions));
            config = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(config));
        }
    }

    static Result<MutationRequest> mutation(String json) {
        return boundary("INVALID_EDITOR_MUTATION", "Incomplete editor mutation request", () -> {
            EditorMutationWire wire = json(json, EditorMutationWire.class);
            try {
                UUID session = uuid(wire.sessionId());
                ResourceLocation book = id(wire.bookId(), "bookId");
                String revision = text(wire.draftRevision(), "draftRevision", 32767, false);
                if (wire.action() == null) throw invalid(null, "action", "Incomplete editor mutation request");
                AuthoringMutationAction action = AuthoringMutationAction.fromWire(wire.action()).orElseThrow(() ->
                        invalid("UNKNOWN_EDITOR_MUTATION", "action", "Unknown editor mutation action"));
                ResourceLocation target = optionalId(wire.targetId(), "targetId");
                ResourceLocation parent = optionalId(wire.parentId(), "parentId");
                ResourceLocation source = optionalId(wire.sourceId(), "sourceId");
                switch (action) {
                    case UNDO, REDO, REVIEW, MOVE_QUESTS, COPY_QUESTS, DELETE_QUESTS -> { }
                    default -> require(target, "targetId");
                }
                switch (action) {
                    case ADD_CHAPTER, UPDATE_CHAPTER, ADD_QUEST, ADD_TASK, UPDATE_TASK, COPY_TASK, PASTE_TASK, MOVE_TASK, DELETE_TASK,
                         ADD_REWARD, UPDATE_REWARD, COPY_REWARD, PASTE_REWARD, MOVE_REWARD, DELETE_REWARD -> require(parent, "parentId");
                    default -> { }
                }
                switch (action) {
                    case COPY_QUEST, ADD_DEPENDENCY, REMOVE_DEPENDENCY, ADD_TASK, UPDATE_TASK, COPY_TASK,
                         ADD_REWARD, UPDATE_REWARD, COPY_REWARD -> require(source, "sourceId");
                    default -> { }
                }
                if (!Double.isFinite(wire.x()) || !Double.isFinite(wire.y()))
                    throw invalid("INVALID_EDITOR_MUTATION", "position", "Moved-node entries require unique IDs and finite coordinates");
                var positions = new java.util.LinkedHashMap<ResourceLocation, Position>();
                if (wire.positions() != null) {
                    if (wire.positions().size() > BrnQuestConstants.MAX_QUESTS)
                        throw invalid("INVALID_EDITOR_MUTATION", "positions", "Moved-node list is empty or exceeds the editor limit");
                    for (PositionWire position : wire.positions()) {
                        if (position == null) throw invalid(null, "positions", "Missing moved-node entry");
                        ResourceLocation positionId = id(position.questId(), "positions.questId");
                        if (!Double.isFinite(position.x()) || !Double.isFinite(position.y())
                                || positions.putIfAbsent(positionId, new Position(position.x(), position.y())) != null)
                            throw invalid("INVALID_EDITOR_MUTATION", "positions", "Moved-node entries require unique IDs and finite coordinates");
                    }
                }
                if ((action == AuthoringMutationAction.MOVE_QUESTS || action == AuthoringMutationAction.COPY_QUESTS
                        || action == AuthoringMutationAction.DELETE_QUESTS) && positions.isEmpty())
                    throw invalid("INVALID_EDITOR_MUTATION", "positions", "Moved-node list is empty or exceeds the editor limit");
                var config = boundedConfig(wire.config());
                if (action == AuthoringMutationAction.PASTE_TASK || action == AuthoringMutationAction.PASTE_REWARD) {
                    var snapshot = yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.decode(config.get("snapshot"));
                    snapshot.requireDestination(book, action == AuthoringMutationAction.PASTE_TASK);
                }
                if (action == AuthoringMutationAction.PASTE_QUESTS)
                    yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot.decode(config.get("snapshot")).requireDestination(book);
                String title = wire.title() == null ? "" : wire.title();
                text(title, "title", 32767, true);
                String claim = action == AuthoringMutationAction.ADD_REWARD ? "" : "manual";
                String locale = "";
                if (action == AuthoringMutationAction.UPDATE_REWARD || (action == AuthoringMutationAction.ADD_REWARD && !title.isBlank())) {
                    String policy = title.strip();
                    if (policy.length() > 64 || !yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy.isKnown(policy))
                        throw invalid("INVALID_EDITOR_MUTATION", "claim_policy", "Reward claim policy must be manual, auto_visible, auto_silent, or auto_hidden");
                    claim = yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy.parse(policy).serializedName();
                } else if (action == AuthoringMutationAction.UPDATE_QUEST_TRANSLATION) {
                    locale = yourscraft.jasdewstarfield.brnquest.data.BookLocalization.normalizeLocale(title);
                    if (!locale.matches("[a-z0-9_]{2,16}")) throw invalid(null, "title", "Locale must use a code such as en_us");
                    text(config.getOrDefault("title", ""), "config.title", 256, true);
                    text(config.getOrDefault("subtitle", ""), "config.subtitle", 256, true);
                    text(config.getOrDefault("description", ""), "config.description", 32768, true);
                }
                if (config.containsKey(yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits.FIELD)) {
                    String kind = switch (action) {
                        case UPDATE_BOOK_PROPERTIES -> "book";
                        case UPDATE_GROUP -> "chapter_group";
                        case UPDATE_CHAPTER -> "chapter";
                        case UPDATE_QUEST_TRANSLATION -> "quest";
                        default -> throw invalid(null, "config.text_field", "Localized text is unsupported for this action");
                    };
                    try {
                        yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits.validate(kind,
                                config.get(yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits.FIELD),
                                yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits.values(config));
                    } catch (IllegalArgumentException exception) {
                        throw invalid(null, "config.text_field", exception.getMessage());
                    }
                }
                if (config.containsKey("quest_defaults")) {
                    try {
                        yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults.fromJson(
                                com.google.gson.JsonParser.parseString(config.get("quest_defaults")));
                    } catch (RuntimeException exception) { throw invalid(null, "config.quest_defaults", "Invalid creation template"); }
                }
                // Historical structural titles are truncated, while config and translations are preserved exactly.
                return new MutationRequest(session, book, revision, action, target, parent, source,
                        title.length() <= 256 ? title : title.substring(0, 256), wire.targetIndex(), wire.x(), wire.y(),
                        positions, config, claim, locale);
            } catch (InvalidInput invalid) {
                // Typed field errors stay attached to the original entry, even when the replacement ID is invalid.
                String objectId = "UPDATE_TASK".equals(wire.action()) || "UPDATE_REWARD".equals(wire.action())
                        ? wire.sourceId() : wire.targetId();
                Failure failure = invalid.failure;
                throw new InvalidInput(new Failure(failure.code(), failure.path(), failure.message(),
                        objectId == null ? "" : objectId, failure.status()));
            }
        });
    }

    private static ResourceLocation optionalId(String value, String path) {
        return value == null || value.isBlank() ? null : id(value, path);
    }

    private static void require(ResourceLocation value, String path) {
        if (value == null) throw invalid(null, path, "A valid namespaced ID is required");
    }
    /** Preserve the live route's existing conflict code even when its book identifier cannot be parsed. */
    static Result<OpenRequest> live(String bookId) {
        var decoded = open(bookId, "");
        return decoded.success() ? decoded : new Result<>(null,
                new Failure("LIVE_BOOK_CHANGED", "bookId", "Displayed book is no longer active", "", FailureStatus.CONFLICT));
    }
}

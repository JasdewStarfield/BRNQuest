package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;
import java.util.*;

/** Staged input values keep placeholders separate from saved overrides. */
final class EditorPropertyValues {
    enum Scope {
        INSTANCE, BOOK, CHAPTER;
        boolean template() { return this != INSTANCE; }
        String defaultLabelKey() {
            return this == CHAPTER ? "screen.brnquest.defaults.inherit" : "screen.brnquest.defaults.system";
        }
    }
    enum Source { SAVED, SYSTEM, BOOK, BOOK_SYSTEM }

    private final Scope scope;
    private final List<QuestPropertySchema.Property> properties;
    private final Map<String, String> inherited;
    private final Map<String, String> values = new TreeMap<>();

    EditorPropertyValues(Scope scope, List<QuestPropertySchema.Property> properties,
                         Map<String, String> initial, Map<String, String> inherited) {
        this.scope = scope; this.properties = List.copyOf(properties); this.inherited = Map.copyOf(inherited);
        for (var property : properties) {
            String value = initial.get(property.key());
            if (value == null && !scope.template()) value = property.coreDefault();
            // A saved number remains concrete even when it equals a preset or the system default.
            // Value equality cannot tell us where it came from, or establish an inheritance link.
            if (value != null) values.put(property.key(), value);
        }
    }

    String value(String key) { return values.get(key); }
    String fallback(QuestPropertySchema.Property property) {
        // A chapter inherits the book; a book has only the registered system default.
        return scope == Scope.CHAPTER ? inherited.getOrDefault(property.key(), property.coreDefault()) : property.coreDefault();
    }
    Source source(QuestPropertySchema.Property property) {
        return values.containsKey(property.key()) ? Source.SAVED : fallbackSource(property);
    }
    private Source fallbackSource(QuestPropertySchema.Property property) {
        if (scope != Scope.CHAPTER) return Source.SYSTEM;
        return inherited.containsKey(property.key()) ? Source.BOOK : Source.BOOK_SYSTEM;
    }
    String hintKey(QuestPropertySchema.Property property) {
        return switch (fallbackSource(property)) {
            case BOOK -> "screen.brnquest.defaults.hint.book";
            case BOOK_SYSTEM -> "screen.brnquest.defaults.hint.book_system";
            default -> "screen.brnquest.defaults.hint.system";
        };
    }
    void edit(QuestPropertySchema.Property property, String value) {
        if (value.isBlank() && (scope.template() || numeric(property))) values.remove(property.key());
        else values.put(property.key(), value);
    }
    Map<String, String> confirmed() {
        var result = new TreeMap<>(values);
        // Empty instance inputs reset to concrete defaults; empty templates stay sparse for inheritance.
        if (!scope.template()) properties.forEach(property -> result.putIfAbsent(property.key(), property.coreDefault()));
        return QuestPropertySchema.CATALOG.normalize(result, properties);
    }
    private static boolean numeric(QuestPropertySchema.Property property) {
        return property.field().valueType() == ConfigValueType.INTEGER || property.field().valueType() == ConfigValueType.DECIMAL;
    }
}

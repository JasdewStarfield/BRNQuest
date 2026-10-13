package yourscraft.jasdewstarfield.brnquest.editor;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A synthetic declaration must reach instance/default groups, validation and storage without extra field lists. */
class QuestPropertySchemaTest {
    private static QuestPropertySchema.Catalog with(QuestPropertySchema.Property property) {
        var registrations = new ArrayList<>(QuestPropertySchema.CATALOG.properties());
        registrations.add(property);
        return new QuestPropertySchema.Catalog(registrations);
    }
    @Test void oneNewRegistrationProducesTheSamePropertyInAllThreeScopesAndPersistsItsDefault() {
        var field = ConfigFieldDescriptor.field("example:notify", ConfigValueType.BOOLEAN).withDefault("false").withLabel("example.notify");
        var property = new QuestPropertySchema.Property(field, "notifications", QuestPropertySchema.Storage.EXTENSION, "example:notify", true);
        var catalog = with(property);
        assertSame(property, catalog.sections(catalog.properties()).get("notifications").getLast());
        // Chapter and book receive the same generated default definitions as their instance form.
        var bookDefaults = catalog.sections(catalog.defaults());
        var chapterDefaults = catalog.sections(catalog.defaults());
        assertSame(property, bookDefaults.get("notifications").getLast());
        assertEquals(bookDefaults, chapterDefaults);
        var selected = catalog.normalize(Map.of("example:notify", "true"), catalog.defaults());
        assertTrue(catalog.json(selected).get("example:notify").getAsBoolean());
        var stored = catalog.extensions(Map.of("opaque:extra", "kept"), selected);
        assertEquals(Map.of("opaque:extra", "kept", "example:notify", "true"), stored);
        assertEquals(Map.of("opaque:extra", "kept"), catalog.extensions(stored, Map.of("example:notify", "false")));
    }
    @Test void explicitDefaultEligibilityAndDuplicateChecksBelongToRegistration() {
        var property = new QuestPropertySchema.Property(ConfigFieldDescriptor.field("example:instance", ConfigValueType.TEXT)
                .withDefault("").withLabel("example.instance"), "appearance", QuestPropertySchema.Storage.EXTENSION, "example:instance", false);
        var catalog = with(property);
        assertTrue(catalog.properties().contains(property));
        assertFalse(catalog.defaults().contains(property));
        assertThrows(IllegalArgumentException.class, () -> catalog.normalize(Map.of(property.key(), "value"), catalog.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new QuestPropertySchema.Catalog(List.of(property, property)));
    }
    @Test void registeredTypeAndRangeValidationAlsoDriveDefaultSerialization() {
        var property = new QuestPropertySchema.Property(ConfigFieldDescriptor.field("example:count", ConfigValueType.INTEGER)
                .withDefault("2").withRange(1, 8).withLabel("example.count"), "completion", QuestPropertySchema.Storage.EXTENSION, "example:count", true);
        var catalog = with(property);
        assertEquals(4, catalog.json(catalog.normalize(Map.of(property.key(), "04"), catalog.defaults())).get(property.key()).getAsInt());
        for (String value : List.of("0", "9", "NaN", "1.5"))
            assertThrows(IllegalArgumentException.class, () -> catalog.normalize(Map.of(property.key(), value), catalog.defaults()));
    }
    @Test void nativeCodecsAndRegisteredValuesRoundTripEveryBehaviorAndAppearanceProperty() {
        var values = new TreeMap<String, String>();
        for (var property : QuestPropertySchema.CATALOG.properties()) {
            String value = switch (property.field().valueType()) {
                case BOOLEAN -> Boolean.toString(!Boolean.parseBoolean(property.coreDefault()));
                case INTEGER -> "3";
                case DECIMAL -> "2.0";
                case ENUM -> property.key().equals("hide_dependency_lines") ? "false"
                        : property.field().allowedValues().stream().filter(option -> !option.equals(property.coreDefault())).findFirst().orElseThrow();
                default -> "custom_shape";
            };
            values.put(property.key(), value);
        }
        var appearance = QuestPropertySchema.appearance(values);
        var behavior = QuestPropertySchema.behavior(values);
        var extensions = QuestPropertySchema.CATALOG.extensions(Map.of("opaque:extra", "kept"), values);
        assertEquals(values, QuestPropertySchema.values(appearance, behavior, extensions));
        assertEquals(3, behavior.minimumRequiredDependencies());
        assertEquals(false, appearance.hideDependencyLines());
        assertEquals(QuestBehavior.DEFAULT, QuestPropertySchema.behavior(Map.of()));
        assertEquals(QuestAppearance.DEFAULT, QuestPropertySchema.appearance(Map.of()));
    }
}

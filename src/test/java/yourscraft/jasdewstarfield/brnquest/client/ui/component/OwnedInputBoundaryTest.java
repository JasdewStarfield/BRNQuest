package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Migration gate: owned inputs have no vanilla-input inheritance, accessors or behavior hooks in the artifact. */
class OwnedInputBoundaryTest {
    @Test void widgetsOwnTheirEditingStateAndMixinConfigurationContainsOnlyTheKeptHooks() throws Exception {
        assertEquals(AbstractWidget.class,EditorTextField.class.getSuperclass());
        assertEquals(AbstractScrollWidget.class,EditorSourceField.class.getSuperclass());
        assertTrue(EditorTextInput.class.isAssignableFrom(EditorTextField.class));
        assertTrue(EditorTextInput.class.isAssignableFrom(EditorSourceField.class));
        try(var resource=getClass().getClassLoader().getResourceAsStream("brnquest.client.mixins.json")) {
            assertNotNull(resource);
            var client=JsonParser.parseReader(new InputStreamReader(resource,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("client");
            var names=new java.util.HashSet<String>(); client.forEach(name->names.add(name.getAsString()));
            assertEquals(Set.of("TextDebugGuiGraphicsMixin","TextDebugFontMixin","TextDebugWidgetMixin","ClientTextTooltipAccessor"),names);
        }
        String base="yourscraft/jasdewstarfield/brnquest/client/";
        for(String name:new String[]{"NativeEditBoxMixin","NativeMultilineTextMixin","EditBoxSelectionAccessor","MultiLineEditBoxAccessor","MultilineTextFieldAccessor"})
            assertNull(getClass().getClassLoader().getResource(base+"mixin/"+name+".class"));
        assertNull(getClass().getClassLoader().getResource(base+"ui/component/RawTextInput.class"));
    }
    @Test void authoringConsumersNoLongerReachInsideVanillaTextBuffers() throws Exception {
        Path root=Path.of(System.getProperty("brnquest.projectDir"),"src/main/java/yourscraft/jasdewstarfield/brnquest/client/ui");
        try(var paths=Files.walk(root)) {
            for(Path path:paths.filter(file->file.toString().endsWith(".java")).toList()) {
                String source=Files.readString(path,StandardCharsets.UTF_8);
                for(String obsolete:new String[]{"brnquest$rawText","brnquest$textField","EditBoxSelectionAccessor","MultilineTextFieldAccessor"})
                    assertFalse(source.contains(obsolete),path.toString());
            }
        }
    }
}

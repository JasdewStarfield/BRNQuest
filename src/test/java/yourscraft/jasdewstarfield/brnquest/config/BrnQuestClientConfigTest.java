package yourscraft.jasdewstarfield.brnquest.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrnQuestClientConfigTest {
    @Test void textDiagnosticsAreOptIn() {
        assertEquals(false, BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.textLayoutDebug));
        assertEquals(java.util.List.of("debug", "textLayoutDebug"), BrnQuestClientConfig.VALUES.textLayoutDebug.getPath());
    }
    @Test void scrollingDefaultsMatchTheEditorBaseline() {
        assertEquals(24.0, BrnQuestClientConfig.VALUES.scrollStep.getDefault(), 0.000001);
        assertEquals(12.0, BrnQuestClientConfig.VALUES.smoothSpeed.getDefault(), 0.000001);
        assertEquals(12.0, BrnQuestClientConfig.VALUES.zoomSmoothSpeed.getDefault(), 0.000001);
        assertEquals(14.0, BrnQuestClientConfig.VALUES.drawerSmoothSpeed.getDefault(), 0.000001);
        assertEquals(10.0, BrnQuestClientConfig.VALUES.focusSmoothSpeed.getDefault(), 0.000001);
        assertEquals(true, BrnQuestClientConfig.VALUES.autoFocusSelectedQuest.getDefault());
    }
}

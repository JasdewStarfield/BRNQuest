package yourscraft.jasdewstarfield.brnquest.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrnQuestClientConfigTest {
    @Test void trackingHudHasIndependentCompactDefaults() {
        assertEquals(.75,BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.hudScale));
        assertEquals(240,BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.hudMaxWidth));
        assertEquals(java.util.List.of("trackingHud","hudScale"),BrnQuestClientConfig.VALUES.hudScale.getPath());
        assertEquals(java.util.List.of("trackingHud","hudMaxWidth"),BrnQuestClientConfig.VALUES.hudMaxWidth.getPath());
    }

    @Test void notificationDefaultsRemainCompactAndTimingIsConfigurable() {
        assertEquals(280, BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationWidth));
        assertEquals(4.0, BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationDurationSeconds));
        assertEquals(java.util.List.of("notifications", "notificationWidth"), BrnQuestClientConfig.VALUES.notificationWidth.getPath());
        assertEquals(java.util.List.of("notifications", "notificationDurationSeconds"), BrnQuestClientConfig.VALUES.notificationDurationSeconds.getPath());
    }
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

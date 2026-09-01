package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestAppearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestNodeGeometryTest {
    @Test
    void hitRadiusTracksAuthoredVisualSizeInBothDirections() {
        QuestAppearance small = new QuestAppearance("circle", 0.5, 1.0, 0.0);
        QuestAppearance normal = QuestAppearance.DEFAULT;
        QuestAppearance large = new QuestAppearance("square", 2.0, 1.0, 0.0);

        assertEquals(9, QuestNodeGeometry.visualSize(18, small));
        assertEquals(6, QuestNodeGeometry.hitRadius(18, small));
        assertEquals(11, QuestNodeGeometry.hitRadius(18, normal));
        assertEquals(20, QuestNodeGeometry.hitRadius(18, large));
        assertTrue(QuestNodeGeometry.hitRadius(18, small) < QuestNodeGeometry.hitRadius(18, normal));
        assertTrue(QuestNodeGeometry.hitRadius(18, large) > QuestNodeGeometry.hitRadius(18, normal));
    }

    @Test
    void minimumWidthAffectsRenderingAndInputTogether() {
        QuestAppearance minimum = new QuestAppearance("diamond", 0.5, 1.0, 1.5);
        assertEquals(27, QuestNodeGeometry.visualSize(18, minimum));
        assertEquals(15, QuestNodeGeometry.hitRadius(18, minimum));
    }
}

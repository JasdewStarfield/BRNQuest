package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The manual mode and divider must share geometry with both rendered panes at every window size. */
class TextEditorLayoutTest {
    @Test void singlePaneModesUseTheFullWritingWidthEvenOnAWideWindow() {
        for (var view:new TextEditorLayout.View[]{TextEditorLayout.View.SOURCE,TextEditorLayout.View.PREVIEW}) {
            var layout=new TextEditorLayout(1000,600,view,.2,1,480);
            assertFalse(layout.split);
            assertEquals(layout.body,layout.source);
            assertEquals(layout.body,layout.preview);
        }
    }
    @Test void narrowSplitCanBeChosenManuallyAndDividerClampsBothPanes() {
        for(double ratio:new double[]{-1,.25,.5,.75,2}) {
            var layout=new TextEditorLayout(360,340,TextEditorLayout.View.SPLIT,ratio,0,480);
            assertTrue(layout.split);
            assertTrue(layout.source.width()>=TextEditorLayout.MINIMUM_PANE);
            assertTrue(layout.preview.width()>=TextEditorLayout.MINIMUM_PANE);
            assertEquals(layout.source.right(),layout.divider.left());
            assertEquals(layout.divider.right(),layout.preview.left());
            assertEquals(layout.body.width(),layout.source.width()+layout.divider.width()+layout.preview.width());
        }
    }
    @Test void temporarySmallWindowDoesNotRequireChangingThePreferredModeOrRatio() {
        assertFalse(new TextEditorLayout(220,300,TextEditorLayout.View.SPLIT,.7,0,480).split);
        var restored=new TextEditorLayout(800,500,TextEditorLayout.View.SPLIT,.7,0,480);
        assertTrue(restored.split);
        assertTrue(restored.source.width()>restored.preview.width());
    }
    @Test void writingSpaceAndFooterStayInsideTheActualWindow() {
        for (int height:new int[]{180,240,300,400,680}) {
            var layout=new TextEditorLayout(700,height,TextEditorLayout.View.SPLIT,.5,0,480);
            assertTrue(layout.panel.top()>=0);
            assertTrue(layout.panel.bottom()<=height);
            assertTrue(layout.body.top()>=layout.panel.top());
            assertTrue(layout.body.bottom()<=layout.panel.bottom()-48);
            if(layout.panel.height()>=400) assertTrue(layout.body.height()>=layout.panel.height()*.6);
            else if(layout.panel.height()>=300) assertTrue(layout.body.height()>=layout.panel.height()*.5);
        }
    }
}

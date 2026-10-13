package yourscraft.jasdewstarfield.brnquest.compat.imblocker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Public-interface protocol fixtures exercise both scale APIs without loading an IME native library. */
class ImBlockerBindingTest {
    public static final class FakeFocus { public static boolean isTrackingFocus; }
    public record FakeRectangle(int x,int y,int width,int height) {}
    public record FakePoint(int x,int y) {}
    public static final class FakeContainer {
        Object owner; int gains,losses,probes,moves;
        public void requestFocus(FakeWidget widget) { owner=widget; gains++; }
        public void removeFocus(FakeWidget widget) { if(owner==widget) owner=null; losses++; }
        public void switchFocus(FakeWidget widget) { owner=widget; probes++; }
    }
    public interface FakeWidget {
        FakeContainer CONTAINER=new FakeContainer();
        boolean getPreferredState(); boolean getPreferredEnglishState(); boolean isRenderable();
        FakeRectangle getBoundsAbs(); FakePoint getCaretPos(); int getFontHeight();
        default double getGuiScale() { return 3; }
        default FakeContainer getFocusContainer() { return CONTAINER; }
        default void imblocker$onFocusGained() { CONTAINER.requestFocus(this); }
        default void imblocker$onFocusLost() { CONTAINER.removeFocus(this); }
        default void imblocker$onBoundsChanged() { CONTAINER.moves++; }
    }
    public interface DpiWidget extends FakeWidget {
        default double getInternalGuiScale() { return 3; }
        @Override default double getGuiScale() { return getInternalGuiScale()/1.5; }
    }
    private ImBlockerBinding binding(Class<?> api) throws Exception {
        return new ImBlockerBinding(api,FakeRectangle.class,FakePoint.class,FakeFocus.class);
    }
    @Test void focusProbeGeometryAndReleaseUseThePublicProtocol() throws Exception {
        var binding=binding(FakeWidget.class);
        var geometry=new ImBlockerBinding.Geometry(10,20,200,80,15,12,9);
        boolean[] available={true}; binding.acquire(()->geometry,()->available[0]);
        var proxy=(FakeWidget)FakeWidget.CONTAINER.owner;
        assertTrue(proxy.getPreferredState()); assertFalse(proxy.getPreferredEnglishState());
        assertEquals(new FakeRectangle(30,60,600,240),proxy.getBoundsAbs());
        assertEquals(new FakePoint(45,36),proxy.getCaretPos()); assertEquals(9,proxy.getFontHeight());
        int probes=FakeWidget.CONTAINER.probes;
        FakeFocus.isTrackingFocus=false; assertFalse(binding.probe());
        FakeFocus.isTrackingFocus=true; assertTrue(binding.probe()); assertEquals(probes+1,FakeWidget.CONTAINER.probes);
        FakeFocus.isTrackingFocus=false;
        available[0]=false; assertFalse(proxy.isRenderable()); binding.moved();
        binding.release(); assertNull(FakeWidget.CONTAINER.owner);
        int losses=FakeWidget.CONTAINER.losses; binding.release(); assertEquals(losses,FakeWidget.CONTAINER.losses);
    }
    @Test void newerDpiDefaultIsUsedWithoutHardcodingTheScale() throws Exception {
        var binding=binding(DpiWidget.class);
        binding.acquire(()->new ImBlockerBinding.Geometry(1,2,3,4,5,6,9),()->true);
        var proxy=(FakeWidget)FakeWidget.CONTAINER.owner;
        assertEquals(new FakeRectangle(2,4,6,8),proxy.getBoundsAbs()); assertEquals(new FakePoint(10,12),proxy.getCaretPos()); binding.release();
    }
    @Test void absentApiAndUnsupportedProtocolAreRejectedAtTheBindingBoundary() {
        assertThrows(ClassNotFoundException.class,()->ImBlockerBinding.load(new ClassLoader(null) {}));
        assertThrows(NoSuchMethodException.class,()->binding(Runnable.class));
    }
    @Test void fractionalDpiScaleProducesPhysicalPixelCoordinates() throws Exception {
        var binding=binding(FractionalDpiWidget.class);
        binding.acquire(()->new ImBlockerBinding.Geometry(3,5,9,11,7,3,9),()->true);
        var proxy=(FakeWidget)FakeWidget.CONTAINER.owner;
        assertEquals(new FakeRectangle(4,7,13,16),proxy.getBoundsAbs());
        assertEquals(new FakePoint(10,4),proxy.getCaretPos()); binding.release();
    }
    public interface FractionalDpiWidget extends FakeWidget {
        @Override default double getGuiScale() { return 1.5; }
    }
}

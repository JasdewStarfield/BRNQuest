package yourscraft.jasdewstarfield.brnquest.compat.imblocker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Reflective adapter to IMBlocker's public focus interface; no foreign class is linked into the mod. */
final class ImBlockerBinding {
    record Geometry(int x,int y,int width,int height,int caretX,int caretY,int fontHeight) {}
    private final Class<?> widget;
    private final Constructor<?> rectangle,point;
    private final Method gain,lose,boundsChanged,scale,container,switchFocus;
    private final Field tracking;
    private Object proxy;

    static ImBlockerBinding load(ClassLoader loader) throws ReflectiveOperationException {
        String api="io.github.reserveword.imblocker.common.gui.";
        return new ImBlockerBinding(Class.forName(api+"MinecraftFocusableWidget",false,loader),
                Class.forName(api+"Rectangle",false,loader),Class.forName(api+"Point",false,loader),
                Class.forName(api+"FocusManager",false,loader));
    }
    /** Explicit types make protocol fixtures independent of the optional mod and its native library. */
    ImBlockerBinding(Class<?> widget,Class<?> rectangle,Class<?> point,Class<?> focus) throws ReflectiveOperationException {
        this.widget=widget;
        // The unscaled integer constructors are stable across the 5.4 and 5.6 APIs; their scaled overloads changed.
        this.rectangle=rectangle.getConstructor(int.class,int.class,int.class,int.class);
        this.point=point.getConstructor(int.class,int.class);
        gain=widget.getMethod("imblocker$onFocusGained"); lose=widget.getMethod("imblocker$onFocusLost");
        boundsChanged=widget.getMethod("imblocker$onBoundsChanged"); scale=widget.getMethod("getGuiScale");
        container=widget.getMethod("getFocusContainer");
        switchFocus=java.util.Arrays.stream(container.getReturnType().getMethods())
                .filter(method->method.getName().equals("switchFocus") && method.getParameterCount()==1
                        && method.getParameterTypes()[0].isAssignableFrom(widget)).findFirst()
                .orElseThrow(()->new NoSuchMethodException("IMBlocker switchFocus"));
        tracking=focus.getField("isTrackingFocus");
    }
    void acquire(Supplier<Geometry> geometry,BooleanSupplier available) throws ReflectiveOperationException {
        release();
        proxy=Proxy.newProxyInstance(widget.getClassLoader(),new Class<?>[]{widget},(self,method,args)-> {
            // Leave scale/DPI and version-specific native calls to IMBlocker's interface defaults.
            return switch(method.getName()) {
                case "equals" -> self==args[0];
                case "hashCode" -> System.identityHashCode(self);
                case "toString" -> "BRNQuest text input";
                case "getPreferredState" -> true;
                case "getPreferredEnglishState" -> false;
                case "isRenderable" -> available.getAsBoolean();
                case "getFontHeight" -> geometry.get().fontHeight();
                case "getBoundsAbs" -> {
                    var box=geometry.get(); double factor=((Number)scale.invoke(self)).doubleValue();
                    yield rectangle.newInstance(pixel(factor,box.x()),pixel(factor,box.y()),pixel(factor,box.width()),pixel(factor,box.height()));
                }
                case "getCaretPos" -> {
                    var box=geometry.get(); double factor=((Number)scale.invoke(self)).doubleValue();
                    yield point.newInstance(pixel(factor,box.caretX()),pixel(factor,box.caretY()));
                }
                default -> {
                    if(!method.isDefault()) throw new UnsupportedOperationException(method.toString());
                    yield InvocationHandler.invokeDefault(self,method,args==null?new Object[0]:args);
                }
            };
        });
        gain.invoke(proxy);
    }
    /** Convert GUI units to physical pixels once, using the scale supplied by IMBlocker's default DPI policy. */
    private static int pixel(double scale,int coordinate) { return (int)(scale*coordinate); }
    void release() throws ReflectiveOperationException {
        if(proxy!=null) {
            Object previous=proxy; proxy=null; lose.invoke(previous);
        }
    }
    void moved() throws ReflectiveOperationException { if(proxy!=null) boundsChanged.invoke(proxy); }
    boolean probe() throws ReflectiveOperationException {
        if(proxy==null || !tracking.getBoolean(null)) return false;
        // Character simulation asks the real focused widget to acknowledge ownership; NUL never reaches the buffer.
        switchFocus.invoke(container.invoke(proxy),proxy); return true;
    }
}

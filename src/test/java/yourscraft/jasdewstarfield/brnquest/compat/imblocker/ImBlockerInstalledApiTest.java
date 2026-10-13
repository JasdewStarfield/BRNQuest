package yourscraft.jasdewstarfield.brnquest.compat.imblocker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.URLClassLoader;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Reads public contracts from a supplied installed JAR; never initializes its native IME or copies it into outputs. */
class ImBlockerInstalledApiTest {
    @Test
    @EnabledIfSystemProperty(named="brnquest.imBlockerApiJar",matches=".+")
    void installedApiBindsWithoutNativeInitializationAndOffersStablePixelConstructors() throws Exception {
        Path jar=Path.of(System.getProperty("brnquest.imBlockerApiJar"));
        try(var loader=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},getClass().getClassLoader())) {
            assertNotNull(ImBlockerBinding.load(loader));
            String api="io.github.reserveword.imblocker.common.gui.";
            Class<?> rectangle=Class.forName(api+"Rectangle",false,loader);
            Class<?> point=Class.forName(api+"Point",false,loader);
            assertSame(loader,rectangle.getClassLoader()); // The check must use the supplied mod, not a test fixture.
            Object box=rectangle.getConstructor(int.class,int.class,int.class,int.class).newInstance(30,60,600,240);
            Object caret=point.getConstructor(int.class,int.class).newInstance(45,36);
            assertEquals(30,rectangle.getMethod("x").invoke(box)); assertEquals(240,rectangle.getMethod("height").invoke(box));
            assertEquals(45,point.getMethod("x").invoke(caret)); assertEquals(36,point.getMethod("y").invoke(caret));
        }
    }
}

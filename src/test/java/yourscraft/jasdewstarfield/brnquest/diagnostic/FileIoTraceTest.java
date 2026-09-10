package yourscraft.jasdewstarfield.brnquest.diagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostics capture the failure scene without changing the action, its exception, or the file contents. */
class FileIoTraceTest {
    @TempDir Path directory;
    @Test void capturesPathsThreadAndOriginalErrorBeforeCleanup() throws Exception {
        Path source=directory.resolve("临时.tmp"), target=directory.resolve("receipt.json");
        Files.writeString(source,"private reward content",StandardCharsets.UTF_8);
        Files.writeString(target,"old receipt",StandardCharsets.UTF_8);
        var denied=new AccessDeniedException(source.toString(),target.toString(),"sharing violation");
        var evidence=new AtomicReference<String>(); var captured=new AtomicReference<Exception>();
        assertSame(denied,assertThrows(AccessDeniedException.class,()->FileIoTrace.run("atomic-replace",source,target,
                ()->{throw denied;},(text,error)->{evidence.set(text);captured.set(error);} )));
        assertSame(denied,captured.get());
        String text=evidence.get();
        assertTrue(text.contains("[BRNQuest/FILE_IO]"));
        assertTrue(text.contains("operation=atomic-replace") && text.contains("elapsedMicros="));
        assertTrue(text.contains("event="+ProcessHandle.current().pid()+"-"));
        assertTrue(text.contains(Thread.currentThread().getName()) && text.contains("AccessDeniedException"));
        assertTrue(text.contains(source.toAbsolutePath().normalize().toString()) && text.contains("sharing violation"));
        assertTrue(text.contains("modified=") && text.contains("parent="));
        assertFalse(text.contains("private reward content"));
        assertEquals("old receipt",Files.readString(target,StandardCharsets.UTF_8));
    }
    @Test void successIsSilentAndDiagnosticFailureCannotMaskOriginalError() throws Exception {
        assertEquals("result",FileIoTrace.run("read",directory,null,()->"result",(text,error)->fail("Success must be silent")));
        var original=new NoSuchFileException("missing");
        assertSame(original,assertThrows(NoSuchFileException.class,()->FileIoTrace.run("read",directory.resolve("missing"),null,
                ()->{throw original;},(text,error)->{throw new IllegalStateException("logger unavailable");})));
    }
    @Test void reportsOnlyLiveOverlappingOperationsAndRemovesThemOnExit() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();
        var worker=new Thread(()->{
            try { FileIoTrace.run("held-read",directory.resolve("receipt.json"),null,()->{
                entered.countDown();
                try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("test timeout"); }
                catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new java.io.IOException(error); }
                return null;
            }); } catch(Throwable error) { failure.set(error); }
        },"trace-overlap-test");
        worker.start(); var evidence=new AtomicReference<String>();
        try {
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(AccessDeniedException.class,()->FileIoTrace.run("move",directory,null,
                    ()->{throw new AccessDeniedException("fixture");},(text,error)->evidence.set(text)));
            assertTrue(evidence.get().contains("observed=1"));
            assertTrue(evidence.get().contains("held-read") && evidence.get().contains("trace-overlap-test"));
        } finally { release.countDown(); worker.join(5000); }
        assertFalse(worker.isAlive()); assertNull(failure.get());
        assertThrows(AccessDeniedException.class,()->FileIoTrace.run("move",directory,null,
                ()->{throw new AccessDeniedException("fixture");},(text,error)->evidence.set(text)));
        assertTrue(evidence.get().contains("observed=0"));
    }
}

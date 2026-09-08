package yourscraft.jasdewstarfield.brnquest.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deliberate violations live only in test bytecode, never in the shipped mod. */
class AuthoringArchitectureRuleTest {
    @Test void everyAuthoringBoundaryRejectsAnActualForbiddenDependency() {
        rejects(ArchitectureBoundaryTest.authoringFacadeMustOnlyExposeTransport, AuthoringNetwork.class);
        rejects(ArchitectureBoundaryTest.authoringDecoderMustOnlyDecode, AuthoringRequestDecoder.class);
        rejects(ArchitectureBoundaryTest.authoringHandlersMustNotSendOrLoadClient, AuthoringSessionHandler.class);
        rejects(ArchitectureBoundaryTest.authoringResponseMustNotExecuteServices, AuthoringResponseSender.class);
        rejects(ArchitectureBoundaryTest.authoringRegistrarMustOnlyWire, AuthoringPayloadRegistrar.class);
    }

    private static void rejects(ArchRule rule, Class<?> fixture) {
        assertTrue(rule.evaluate(new ClassFileImporter().importClasses(fixture)).hasViolation(),
                "The rule must reject " + fixture.getSimpleName());
    }

    static class AuthoringNetwork { yourscraft.jasdewstarfield.brnquest.api.AuthorApi forbidden; }
    static class AuthoringRequestDecoder { net.minecraft.server.level.ServerPlayer forbidden; }
    static class AuthoringSessionHandler { net.neoforged.neoforge.network.PacketDistributor forbidden; }
    static class AuthoringResponseSender { yourscraft.jasdewstarfield.brnquest.api.AuthorApi forbidden; }
    static class AuthoringPayloadRegistrar { yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor forbidden; }
}

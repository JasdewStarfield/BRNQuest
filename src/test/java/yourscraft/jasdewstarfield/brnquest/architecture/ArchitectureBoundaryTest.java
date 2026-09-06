package yourscraft.jasdewstarfield.brnquest.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Bytecode-level boundaries for code that must remain safe on a dedicated server. */
@AnalyzeClasses(
        packages = "yourscraft.jasdewstarfield.brnquest",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureBoundaryTest {
    private static final String CLIENT_PACKAGE = "yourscraft.jasdewstarfield.brnquest.client..";
    private static final String MINECRAFT_CLIENT_PACKAGE = "net.minecraft.client..";

    @ArchTest
    static final ArchRule publicApiMustNotLoadClientClasses = noClasses()
            .that().resideInAPackage("yourscraft.jasdewstarfield.brnquest.api..")
            .should().dependOnClassesThat().resideInAnyPackage(CLIENT_PACKAGE, MINECRAFT_CLIENT_PACKAGE)
            .because("public API entry points must remain loadable on a dedicated server");

    @ArchTest
    static final ArchRule publicViewsMustNotExposeImplementationPackages = noClasses()
            .that().resideInAPackage("yourscraft.jasdewstarfield.brnquest.api..")
            .and().haveSimpleNameEndingWith("View")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "yourscraft.jasdewstarfield.brnquest.author..",
                    "yourscraft.jasdewstarfield.brnquest.client..",
                    "yourscraft.jasdewstarfield.brnquest.data..",
                    "yourscraft.jasdewstarfield.brnquest.network..",
                    "yourscraft.jasdewstarfield.brnquest.runtime..",
                    "yourscraft.jasdewstarfield.brnquest.workspace.."
            )
            .because("immutable public projections must not expose storage, networking, or runtime DTOs");

    @ArchTest
    static final ArchRule serverOwnedDomainsMustNotLoadClientClasses = noClasses()
            .that().resideInAnyPackage(
                    "yourscraft.jasdewstarfield.brnquest.author..",
                    "yourscraft.jasdewstarfield.brnquest.data..",
                    "yourscraft.jasdewstarfield.brnquest.diagnostic..",
                    "yourscraft.jasdewstarfield.brnquest.editor..",
                    "yourscraft.jasdewstarfield.brnquest.event..",
                    "yourscraft.jasdewstarfield.brnquest.extension..",
                    "yourscraft.jasdewstarfield.brnquest.owner..",
                    "yourscraft.jasdewstarfield.brnquest.progress..",
                    "yourscraft.jasdewstarfield.brnquest.reward..",
                    "yourscraft.jasdewstarfield.brnquest.runtime..",
                    "yourscraft.jasdewstarfield.brnquest.task..",
                    "yourscraft.jasdewstarfield.brnquest.workspace.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(CLIENT_PACKAGE, MINECRAFT_CLIENT_PACKAGE)
            .because("authoritative gameplay and persistence code must not resolve client-only classes");

    @ArchTest
    static final ArchRule composedClientSectionsMustNotDispatchNetworkRequests = noClasses()
            .that().haveSimpleNameEndingWith("Section")
            .should().dependOnClassesThat().resideInAPackage("yourscraft.jasdewstarfield.brnquest.network..")
            .because("screen sections return semantic intents to the parent instead of owning protocol calls");
}

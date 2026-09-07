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
    // Match composed screen parts and their reusable primitives while leaving top-level Screen dispatchers out.
    private static final String COMPOSED_CLIENT_UI_PATTERN = "yourscraft\\.jasdewstarfield\\.brnquest\\.client\\.ui\\..*"
            + "(Section|Panel|Widget|Renderer|Controller|Interaction|"
            + "NodeDrag|DiagnosticPresentation|TooltipComposer|TypePickerModel|TypedEntryKind|"
            + "TypedPropertyFormModel|ScreenFrameIdentity|FormFields|SelectionFocus)";

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
    static final ArchRule composedClientUiMustNotDispatchNetworkRequests = noClasses()
            .that().haveNameMatching(COMPOSED_CLIENT_UI_PATTERN)
            .should().dependOnClassesThat().resideInAPackage("yourscraft.jasdewstarfield.brnquest.network..")
            .because("composed UI parts return semantic intents to a top-level Screen instead of owning protocol calls");

    @ArchTest
    static final ArchRule composedClientUiMustNotDependOnQuestScreen = noClasses()
            .that().haveNameMatching(COMPOSED_CLIENT_UI_PATTERN)
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "yourscraft.jasdewstarfield.brnquest.client.ui.QuestScreen")
            .because("composed UI parts receive immutable models and frames instead of a whole-Screen backdoor");
}

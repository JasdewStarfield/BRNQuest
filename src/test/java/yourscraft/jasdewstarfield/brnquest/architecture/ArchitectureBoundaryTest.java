package yourscraft.jasdewstarfield.brnquest.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.LocationProvider;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Bytecode-level boundaries for code that must remain safe on a dedicated server. */
@AnalyzeClasses(locations = ArchitectureBoundaryTest.ProductionModules.class)
public class ArchitectureBoundaryTest {
    /** Exact outputs work with custom build directories and exclude deliberate test-only violations. */
    public static final class ProductionModules implements LocationProvider {
        @Override public java.util.Set<Location> get(Class<?> testClass) {
            var build = java.nio.file.Path.of(System.getProperty("brnquest.buildDir"), "classes/java");
            return java.util.Set.of(Location.of(build.resolve("main")), Location.of(build.resolve("builtin")),
                    Location.of(build.resolve("integration")));
        }
    }

    private static final String CLIENT_PACKAGE = "yourscraft.jasdewstarfield.brnquest.client..";
    private static final String MINECRAFT_CLIENT_PACKAGE = "net.minecraft.client..";
    // Match composed screen parts and their reusable primitives while leaving top-level Screen dispatchers out.
    private static final String COMPOSED_CLIENT_UI_PATTERN = "yourscraft\\.jasdewstarfield\\.brnquest\\.client\\.ui\\..*"
            + "(Section|Panel|Widget|Renderer|Controller|Interaction|"
            + "NodeDrag|DiagnosticPresentation|TooltipComposer|TypePickerModel|TypedEntryKind|"
            + "TypedPropertyFormModel|ScreenFrameIdentity|FormFields|SelectionFocus)";

    @ArchTest
    static final ArchRule coreMustNotDependOnBuiltinTypes = noClasses()
            .that().resideInAnyPackage(
                    "yourscraft.jasdewstarfield.brnquest.editor..",
                    "yourscraft.jasdewstarfield.brnquest.network..",
                    "yourscraft.jasdewstarfield.brnquest.task..",
                    "yourscraft.jasdewstarfield.brnquest.reward..",
                    "yourscraft.jasdewstarfield.brnquest.author..",
                    "yourscraft.jasdewstarfield.brnquest.progress..",
                    "yourscraft.jasdewstarfield.brnquest.client..")
            .and().haveNameNotMatching(".*GameTests(\\$.*)?")
            .should().dependOnClassesThat().resideInAPackage("yourscraft.jasdewstarfield.brnquest.builtin..")
            .because("loader bootstrap alone wires built-in plugins into generic registries");

    @ArchTest
    static final ArchRule builtinsMustNotAccessMutableProgress = noClasses()
            .that().resideInAPackage("yourscraft.jasdewstarfield.brnquest.builtin..")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*\\.progress\\.(ProgressEngine|PlayerProgress|QuestProgressData)")
            .because("type recovery reads immutable identities and re-enters the public claim transaction");

    @ArchTest
    static final ArchRule builtinCommonMustNotLoadClientClasses = noClasses()
            .that().resideInAPackage("yourscraft.jasdewstarfield.brnquest.builtin..")
            .and().resideOutsideOfPackages("..client..")
            .and().haveNameNotMatching(".*\\$ClientDelegate")
            .should().dependOnClassesThat().resideInAnyPackage(
                    CLIENT_PACKAGE, MINECRAFT_CLIENT_PACKAGE,
                    "yourscraft.jasdewstarfield.brnquest.builtin.basic.client..",
                    "yourscraft.jasdewstarfield.brnquest.builtin.client..");

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

    // Every split owner is required; deliberate negative fixtures keep these boundaries executable.
    @ArchTest
    static final ArchRule authoringFacadeMustOnlyExposeTransport = noClasses()
            .that().haveNameMatching(".*AuthoringNetwork(\\$.*)?")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*\\.brnquest\\.(api|author|data|runtime|workspace)\\..*"
                    + "|.*(ServerPlayer|AuthoringRequestDecoder|AuthoringResponseSender|Authoring.*Handler)")
            .because("the compatibility facade retains only wire contracts, client sends and registration delegation");

    @ArchTest
    static final ArchRule authoringDecoderMustOnlyDecode = noClasses()
            .that().haveNameMatching(".*AuthoringRequestDecoder(\\$.*)?")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*(ServerPlayer|AuthorApi|.*Service|DraftBookEditor|QuestBookManager|AuthoringNetwork|AuthoringResponseSender|BrnQuestNetwork|PacketDistributor)");

    @ArchTest
    static final ArchRule authoringHandlersMustNotSendOrLoadClient = noClasses()
            .that().haveNameMatching(".*Authoring(Session|Publication|QuestUpdate|Mutation)Handler(\\$.*)?")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*\\.client\\..*|.*(PacketDistributor|BrnQuestNetwork|AuthoringNetwork)");

    @ArchTest
    static final ArchRule authoringResponseMustNotExecuteServices = noClasses()
            .that().haveNameMatching(".*AuthoringResponseSender(\\$.*)?")
            .should().dependOnClassesThat().haveNameMatching(".*(AuthorApi|.*Service|DraftBookEditor|QuestBookManager)");

    @ArchTest
    static final ArchRule authoringRegistrarMustOnlyWire = noClasses()
            .that().haveNameMatching(".*AuthoringPayloadRegistrar(\\$.*)?")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "yourscraft.jasdewstarfield.brnquest.api..",
                    "yourscraft.jasdewstarfield.brnquest.author..",
                    "yourscraft.jasdewstarfield.brnquest.data..",
                    "yourscraft.jasdewstarfield.brnquest.runtime..",
                    "yourscraft.jasdewstarfield.brnquest.workspace..");

    @ArchTest
    static final ArchRule authoringCommonOwnersMustNotLoadClient = noClasses()
            .that().haveNameMatching(".*network\\.Authoring(Network|RequestDecoder|ResponseSender|PayloadRegistrar|"
                    + "SessionHandler|PublicationHandler|QuestUpdateHandler|MutationHandler)")
            .should().dependOnClassesThat().resideInAnyPackage(CLIENT_PACKAGE, MINECRAFT_CLIENT_PACKAGE)
            .because("only the physically guarded nested client delegate may resolve client classes");
}

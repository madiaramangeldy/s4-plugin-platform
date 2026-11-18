package kz.madiyar.plugins.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * R2 at compile level: plugins may depend only on the plugin API and the JDK.
 * (At runtime the same rule is enforced by PluginClassLoader.)
 */
class ArchitectureTest {

    private static final JavaClasses ALL = new ClassFileImporter().importPackages("kz.madiyar.plugins");

    /** Rule for any package that contains plugins. */
    private static ArchRule pluginsUseOnlyApi(String pluginPackage) {
        return classes().that().resideInAPackage(pluginPackage)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(pluginPackage, "kz.madiyar.plugins.api..", "java..")
                .because("a plugin must see only the host API (R2)");
    }

    @Test
    void samplePluginsDependOnlyOnApi() {
        pluginsUseOnlyApi("kz.madiyar.plugins.samples..").check(ALL);
    }

    @Test
    void apiDoesNotDependOnHost() {
        noClasses().that().resideInAPackage("kz.madiyar.plugins.api..")
                .should().dependOnClassesThat().resideInAPackage("kz.madiyar.plugins.host..")
                .because("the API is what plugins compile against; it must not leak host internals")
                .check(ALL);
    }

    /** Proves the rule is not "weak": a plugin that touches host internals is caught. */
    @Test
    void ruleCatchesPluginThatUsesHostInternals() {
        ArchRule rule = pluginsUseOnlyApi("kz.madiyar.plugins.testsupport.sneaky..");
        assertThrows(AssertionError.class, () -> rule.check(ALL));
    }
}

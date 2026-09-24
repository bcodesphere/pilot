package com.bcodesphere.pilot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Pruebas de arquitectura: límites entre módulos (Spring Modulith) y pureza del dominio (ArchUnit).
 * Fuente de las reglas: CLAUDE.md 4.2 y 4.3, ADR-001.
 */
@AnalyzeClasses(packages = "com.bcodesphere.pilot", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaModulosTest {

    /** Los cuatro módulos esperados, con dependencias válidas y sin ciclos. */
    @Test
    void losModulosRespetanSusDependenciasPermitidas() {
        // Caso: contabilidad -> integracion (o cualquier ciclo) debe hacer fallar verify()
        ApplicationModules modulos = ApplicationModules.of(PilotApplication.class);
        assertThat(modulos.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("compartido", "plataforma", "contabilidad", "integracion");
        modulos.verify();
    }

    /** El dominio no depende de Spring (CLAUDE.md 4.3). allowEmptyShould: en F0 aún no hay clases de dominio. */
    @ArchTest
    static final ArchRule DOMINIO_NO_DEPENDE_DE_SPRING = noClasses()
            .that()
            .resideInAPackage("..dominio..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .allowEmptyShould(true);
}

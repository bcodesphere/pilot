package com.bcodesphere.pilot.contabilidad;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de arquitectura propias de contabilidad. Fuente: CLAUDE.md 1.2.8 y 4.2 y ADR-001: un módulo solo usa la API
 * pública (paquete raíz) de otro, nunca sus capas internas ni sus tablas.
 */
@AnalyzeClasses(packages = "com.bcodesphere.pilot", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaContabilidadTest {

    /** Contabilidad no toca las capas internas de plataforma (api, aplicacion, dominio, infraestructura). */
    @ArchTest
    static final ArchRule SIN_TIPOS_INTERNOS_DE_PLATAFORMA = noClasses()
            .that()
            .resideInAPackage("com.bcodesphere.pilot.contabilidad..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.bcodesphere.pilot.plataforma.api..",
                    "com.bcodesphere.pilot.plataforma.aplicacion..",
                    "com.bcodesphere.pilot.plataforma.dominio..",
                    "com.bcodesphere.pilot.plataforma.infraestructura..");

    /** Contabilidad nunca depende de integración (CLAUDE.md 4.2). */
    @ArchTest
    static final ArchRule SIN_DEPENDENCIA_DE_INTEGRACION = noClasses()
            .that()
            .resideInAPackage("com.bcodesphere.pilot.contabilidad..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.bcodesphere.pilot.integracion..")
            .allowEmptyShould(true);

    /** La capa de dominio de contabilidad no depende de sus capas de aplicación, api ni infraestructura (hexagonal). */
    @ArchTest
    static final ArchRule EL_DOMINIO_NO_DEPENDE_DE_LAS_DEMAS_CAPAS = noClasses()
            .that()
            .resideInAPackage("com.bcodesphere.pilot.contabilidad.dominio..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.bcodesphere.pilot.contabilidad.aplicacion..",
                    "com.bcodesphere.pilot.contabilidad.api..",
                    "com.bcodesphere.pilot.contabilidad.infraestructura..");
}

package com.bcodesphere.pilot;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
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

    /**
     * Clases autorizadas a llamar a {@code ContextoEmpresa.ejecutarSinEmpresa} (ADR-026 punto 4). Lista MÍNIMA y
     * explícita:
     *
     * <ul>
     *   <li>la resolución de identidad, la validación de membresía y la consulta de membresías de {@code /me};
     *   <li>{@code ExigirAppInstalada}, que lee solo la tabla global {@code aplicacion} para exigir
     *       {@code X-Empresa-Id} en las rutas de app (F1-05);
     *   <li>{@code AutenticarApiKey}, que busca la API key por prefijo con la función {@code api_key_por_prefijo}
     *       antes de conocer la empresa (F1-06);
     *   <li>{@code ContextoEmpresa}, porque su variante {@code Runnable} delega en la de {@code Supplier}.
     * </ul>
     */
    private static final String[] AUTORIZADAS_SIN_EMPRESA = {
        "com.bcodesphere.pilot.plataforma.ContextoEmpresa",
        "com.bcodesphere.pilot.plataforma.aplicacion.ResolverIdentidad",
        "com.bcodesphere.pilot.plataforma.aplicacion.ValidarMembresia",
        "com.bcodesphere.pilot.plataforma.aplicacion.ConsultarUsuarioActual",
        "com.bcodesphere.pilot.plataforma.aplicacion.ExigirAppInstalada",
        "com.bcodesphere.pilot.plataforma.aplicacion.AutenticarApiKey"
    };

    /**
     * El modo «sin empresa» abre una puerta al aislamiento por RLS (solo tablas globales y funciones de búsqueda), así
     * que solo las clases de la lista pueden usarlo (ADR-026). Cualquier otra clase que lo llame hace fallar la prueba.
     * Incluye las clases anónimas y las lambdas de las clases autorizadas, que se cuentan como parte de ellas.
     */
    @ArchTest
    static final ArchRule SOLO_LAS_AUTORIZADAS_USAN_EL_MODO_SIN_EMPRESA = noClasses()
            .that(
                    new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass>(
                            "no son de la lista de clases autorizadas") {
                        @Override
                        public boolean test(com.tngtech.archunit.core.domain.JavaClass clase) {
                            // Una clase interna o lambda se considera parte de su clase externa
                            String externa = clase.getName().split("\\$")[0];
                            return !java.util.Arrays.asList(AUTORIZADAS_SIN_EMPRESA)
                                    .contains(externa);
                        }
                    })
            .should()
            .callMethodWhere(
                    target(name("ejecutarSinEmpresa")).and(target(owner(assignableTo(ContextoEmpresa.class)))));
}

package com.bcodesphere.pilot.soporte;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Contenedor PostgreSQL compartido por todas las pruebas de integración de la JVM.
 * Usa la misma imagen que infra/docker/compose.dev.yml y el script real 01-roles.sh (sin copiarlo),
 * de modo que el rol pilot_app de las pruebas es idéntico al de desarrollo.
 */
public final class PostgresContenedor {

    /** Imagen idéntica a la del compose de desarrollo (versión exacta). */
    private static final String IMAGEN = "postgres:17.11-alpine";

    /** SOLO PRUEBAS: contraseña del dueño en el contenedor efímero; no es un secreto real. */
    private static final String PASSWORD_DUENIO_PRUEBA = "prueba-duenio-no-es-secreto";

    /** SOLO PRUEBAS: contraseña de pilot_app en el contenedor efímero; no es un secreto real. */
    private static final String PASSWORD_APP_PRUEBA = "prueba-app-no-es-secreto";

    /** Contenedor único: se inicia una vez al cargar la clase y Ryuk lo elimina al terminar la JVM. */
    private static final PostgreSQLContainer POSTGRES = crear();

    private PostgresContenedor() {}

    /** Construye e inicia el contenedor con el script de roles real montado en el directorio de inicialización. */
    private static PostgreSQLContainer crear() {
        // 1. Mismo usuario dueño y base que el compose
        PostgreSQLContainer contenedor = new PostgreSQLContainer(IMAGEN)
                .withDatabaseName("pilot")
                .withUsername("pilot_owner")
                .withPassword(PASSWORD_DUENIO_PRUEBA)
                // 2. Contraseña que 01-roles.sh lee del entorno para crear pilot_app
                .withEnv("PG_APP_PASSWORD", PASSWORD_APP_PRUEBA)
                // 3. Script real, ejecutable (0755) para que la imagen lo ejecute en vez de "sourcearlo"
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../infra/docker/postgres/init/01-roles.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh");
        contenedor.start();
        return contenedor;
    }

    /**
     * DataSource del dueño (pilot_owner), para ejecutar Flyway y sembrar datos.
     *
     * @return conexiones con el dueño del esquema
     */
    public static DataSource dataSourceDuenio() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "pilot_owner", PASSWORD_DUENIO_PRUEBA);
    }

    /**
     * DataSource de la aplicación (pilot_app): sin privilegios de dueño ni BYPASSRLS.
     *
     * @return conexiones con el rol de la aplicación
     */
    public static DataSource dataSourceApp() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "pilot_app", PASSWORD_APP_PRUEBA);
    }

    /**
     * URL JDBC del contenedor, para {@code @DynamicPropertySource} de las pruebas con contexto de Spring.
     *
     * @return URL JDBC de la base de pruebas
     */
    public static String urlJdbc() {
        return POSTGRES.getJdbcUrl();
    }

    /**
     * Usuario del dueño del esquema (para Flyway).
     *
     * @return {@code pilot_owner}
     */
    public static String usuarioDuenio() {
        return "pilot_owner";
    }

    /**
     * Contraseña de prueba del dueño (no es un secreto real).
     *
     * @return contraseña del contenedor efímero
     */
    public static String passwordDuenio() {
        return PASSWORD_DUENIO_PRUEBA;
    }

    /**
     * Usuario de la aplicación, sin privilegios de dueño ni BYPASSRLS.
     *
     * @return {@code pilot_app}
     */
    public static String usuarioApp() {
        return "pilot_app";
    }

    /**
     * Contraseña de prueba de {@code pilot_app} (no es un secreto real).
     *
     * @return contraseña del contenedor efímero
     */
    public static String passwordApp() {
        return PASSWORD_APP_PRUEBA;
    }
}

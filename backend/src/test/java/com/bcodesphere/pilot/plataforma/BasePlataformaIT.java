package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Base de las pruebas de integración de plataforma con contexto de Spring completo.
 * La aplicación se conecta como {@code pilot_app} (sin BYPASSRLS) y Flyway migra como {@code pilot_owner},
 * ambos contra el contenedor compartido de {@link PostgresContenedor} (CLAUDE.md 4.5 y 16.2).
 *
 * <p>Límite del pool de Hikari de las pruebas (F3-06, menor de F3-03): cada combinación distinta de propiedades de
 * prueba crea su propio contexto de Spring cacheado, y cada contexto abre su propio pool contra el mismo PostgreSQL
 * de {@link PostgresContenedor} (una sola instancia para toda la JVM). Con el tamaño por defecto de Hikari (10) y
 * varios contextos en caché (plataforma, contabilidad, aceptación, …) el total se acerca a {@code max_connections}
 * (100) del contenedor. Se fija aquí un pool pequeño para todas las pruebas de plataforma que no diga lo contrario:
 * una subclase que necesite otro valor (p. ej. {@link infraestructura.TransaccionesIT}, que exige EXACTAMENTE una
 * conexión) lo declara con su propio {@code @TestPropertySource}, que sustituye este por clave (las propiedades
 * declaradas localmente tienen precedencia sobre las heredadas).
 */
@SpringBootTest
@TestPropertySource(
        properties = {"spring.datasource.hikari.maximum-pool-size=5", "spring.datasource.hikari.minimum-idle=1"})
public abstract class BasePlataformaIT {

    /** Apunta el datasource y Flyway al contenedor de pruebas, con los roles reales. */
    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // 1. La aplicación usa pilot_app, igual que en desarrollo y producción
        registro.add("spring.datasource.url", PostgresContenedor::urlJdbc);
        registro.add("spring.datasource.username", PostgresContenedor::usuarioApp);
        registro.add("spring.datasource.password", PostgresContenedor::passwordApp);
        // 2. Flyway usa el dueño del esquema
        registro.add("spring.flyway.url", PostgresContenedor::urlJdbc);
        registro.add("spring.flyway.user", PostgresContenedor::usuarioDuenio);
        registro.add("spring.flyway.password", PostgresContenedor::passwordDuenio);
        // 3. Emisor OIDC INALCANZABLE a propósito (puerto 9, "discard"): la aplicación debe arrancar sin Keycloak,
        // porque
        //    el decodificador consulta al emisor en el primer uso y no al arrancar. Los JWT de las pruebas se simulan.
        registro.add("pilot.seguridad.emisor", () -> "http://127.0.0.1:9/realms/pilot");
    }
}

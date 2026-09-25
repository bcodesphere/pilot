package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de las pruebas de integración de plataforma con contexto de Spring completo.
 * La aplicación se conecta como {@code pilot_app} (sin BYPASSRLS) y Flyway migra como {@code pilot_owner},
 * ambos contra el contenedor compartido de {@link PostgresContenedor} (CLAUDE.md 4.5 y 16.2).
 */
@SpringBootTest
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

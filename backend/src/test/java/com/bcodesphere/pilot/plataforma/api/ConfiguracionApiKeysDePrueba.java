package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Configuración de prueba de F1-06: registra controladores bajo {@code /api/v1/integraciones/n8n/**} (solo existen en
 * {@code src/test}; el webhook real es de F5). Sirven para demostrar que la empresa sale de la clave, que el alcance se
 * exige y que una API key nunca satisface un rol de usuario. Solo existen en los contextos que la importan: Spring registra como bean la clase anidada al importar
 * esta configuración (y el escaneo no la ve, porque las pruebas no escanean sus propios anidados).
 */
@TestConfiguration(proxyBeanMethods = false)
class ConfiguracionApiKeysDePrueba {

    /** Controladores de prueba de la cadena de API keys. */
    @Controller
    @RequestMapping("/api/v1/integraciones/n8n")
    @ResponseBody
    public static class ControladorIntegracionPrueba {

        private final JdbcClient jdbc;

        ControladorIntegracionPrueba(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        /** Exige el alcance real y devuelve la empresa y el usuario del contexto (la empresa sale de la clave). */
        @PostMapping("/prueba")
        @PreAuthorize("hasAuthority('SCOPE_integracion:operaciones')")
        public Map<String, String> prueba() {
            return Map.of(
                    "empresaId", ContextoEmpresa.empresaRequerida().valor().toString(),
                    "usuario", ContextoEmpresa.usuarioOSistema());
        }

        /** Exige un alcance que ninguna clave puede tener (V7 solo admite integracion:operaciones). */
        @PostMapping("/prueba-alcance-inexistente")
        @PreAuthorize("hasAuthority('SCOPE_inexistente:alcance')")
        public String pruebaAlcanceInexistente() {
            return "no debería llegar";
        }

        /** Exige un rol de usuario: una API key nunca debe satisfacerlo (ROLE_INTEGRACION fuera de la jerarquía). */
        @PostMapping("/prueba-rol-auditor")
        @PreAuthorize("hasRole('AUDITOR')")
        public String pruebaRolAuditor() {
            return "no debería llegar";
        }

        /** Lista las claves que ve la petición con RLS: solo las de la empresa de la API key usada. */
        @GetMapping("/prueba-claves-visibles")
        @PreAuthorize("hasAuthority('SCOPE_integracion:operaciones')")
        @Transactional(readOnly = true)
        public Map<String, Object> clavesVisibles() {
            return Map.of(
                    "ids",
                    jdbc.sql("SELECT id::text FROM api_key").query(String.class).list());
        }
    }
}

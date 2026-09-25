package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pruebas de la lectura y validación de los claims del token (ADR-028; claims del realm "pilot"). */
class DatosIdentidadTest {

    /** Claims válidos de un usuario típico, sin consentimiento de recomendaciones. */
    private static Map<String, Object> claimsValidos() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "abc-123");
        claims.put("email", "Ana.Perez@Ejemplo.SV");
        claims.put("email_verified", true);
        claims.put("name", "Ana Pérez");
        claims.put("telefono", "70001234");
        return claims;
    }

    /** Caso: el correo pasa a minúsculas, el teléfono se normaliza y sin la casilla el consentimiento es falso. */
    @Test
    void normalizaCorreoYTelefonoYSinCasillaNoHayConsentimiento() {
        DatosIdentidad datos = DatosIdentidad.desdeClaims(claimsValidos());

        assertThat(datos.correo()).isEqualTo("ana.perez@ejemplo.sv");
        assertThat(datos.telefono().valor()).isEqualTo("+50370001234");
        assertThat(datos.nombre()).isEqualTo("Ana Pérez");
        assertThat(datos.recomendacionesAceptadas()).isFalse();
    }

    /** Caso: la casilla marcada llega como booleano verdadero. */
    @Test
    void laCasillaMarcadaSeLee() {
        Map<String, Object> claims = claimsValidos();
        claims.put("recomendaciones_correo", true);

        assertThat(DatosIdentidad.desdeClaims(claims).recomendacionesAceptadas())
                .isTrue();
    }

    /** Caso: sin email_verified verdadero el token no sirve (401 PLT-009); ausente, falso o texto distinto de true. */
    @Test
    void correoSinVerificarSeRechaza() {
        for (Object valor : new Object[] {null, false, "false", "si"}) {
            Map<String, Object> claims = claimsValidos();
            claims.put("email_verified", valor);
            assertThatThrownBy(() -> DatosIdentidad.desdeClaims(claims))
                    .isInstanceOfSatisfying(ExcepcionPlataforma.class, e -> {
                        assertThat(e.codigo()).isEqualTo("PLT-009");
                        assertThat(e.estadoHttp()).isEqualTo(401);
                    });
        }
    }

    /** Caso: sin teléfono válido (ausente o extranjero) el token se rechaza con 401 PLT-009. */
    @Test
    void telefonoAusenteOInvalidoSeRechaza() {
        for (Object valor : new Object[] {null, "", "+13055550100", "abc"}) {
            Map<String, Object> claims = claimsValidos();
            claims.put("telefono", valor);
            assertThatThrownBy(() -> DatosIdentidad.desdeClaims(claims))
                    .isInstanceOfSatisfying(
                            ExcepcionPlataforma.class,
                            e -> assertThat(e.codigo()).isEqualTo("PLT-009"));
        }
    }

    /** Caso: sin sub o sin correo no hay identidad (401 PLT-009). */
    @Test
    void sinSubOSinCorreoSeRechaza() {
        for (String clave : new String[] {"sub", "email"}) {
            Map<String, Object> claims = claimsValidos();
            claims.remove(clave);
            assertThatThrownBy(() -> DatosIdentidad.desdeClaims(claims)).isInstanceOf(ExcepcionPlataforma.class);
        }
    }

    /** Caso: sin nombre en el token se usa el correo para no dejar la columna vacía. */
    @Test
    void sinNombreSeUsaElCorreo() {
        Map<String, Object> claims = claimsValidos();
        claims.remove("name");

        assertThat(DatosIdentidad.desdeClaims(claims).nombre()).isEqualTo("ana.perez@ejemplo.sv");
    }
}

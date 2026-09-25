package com.bcodesphere.pilot.plataforma.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Pruebas de los validadores del token (ADR-008). Son unitarias porque el JWT simulado de MockMvc se salta la
 * decodificación, así que ni el emisor ni la audiencia se comprobarían en las pruebas de la API.
 */
class ValidadoresJwtTest {

    private static final String EMISOR = "http://localhost:8180/realms/pilot";
    private static final String AUDIENCIA = "pilot-api";

    private final OAuth2TokenValidator<Jwt> validador = ValidadoresJwt.validador(EMISOR, AUDIENCIA);

    /** Arma un JWT con el emisor, la audiencia y la vigencia indicados. */
    private static Jwt jwt(String emisor, List<String> audiencia, Instant expira) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("sub-1")
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(expira);
        if (emisor != null) {
            b.issuer(emisor);
        }
        if (audiencia != null) {
            b.audience(audiencia);
        }
        return b.build();
    }

    private OAuth2TokenValidatorResult validar(Jwt jwt) {
        return validador.validate(jwt);
    }

    /** Caso: emisor y audiencia correctos, y vigente → se acepta. */
    @Test
    void tokenValidoSeAcepta() {
        assertThat(validar(jwt(EMISOR, List.of(AUDIENCIA), Instant.now().plusSeconds(300)))
                        .hasErrors())
                .isFalse();
    }

    /** Caso: la audiencia debe incluir pilot-api; un token emitido para otro cliente del realm se rechaza. */
    @Test
    void otraAudienciaSeRechaza() {
        assertThat(validar(jwt(EMISOR, List.of("otra-api"), Instant.now().plusSeconds(300)))
                        .hasErrors())
                .isTrue();
        assertThat(validar(jwt(EMISOR, null, Instant.now().plusSeconds(300))).hasErrors())
                .isTrue();
    }

    /** Caso: si la audiencia lista varias, basta con que incluya pilot-api (Keycloak agrega "account" y otras). */
    @Test
    void variasAudienciasQueIncluyenLaNuestraSeAceptan() {
        assertThat(validar(jwt(
                                EMISOR,
                                List.of("account", AUDIENCIA),
                                Instant.now().plusSeconds(300)))
                        .hasErrors())
                .isFalse();
    }

    /** Caso: un token de otro emisor (otro realm u otro servidor) se rechaza aunque la audiencia coincida. */
    @Test
    void otroEmisorSeRechaza() {
        assertThat(validar(jwt(
                                "http://localhost:8180/realms/otro",
                                List.of(AUDIENCIA),
                                Instant.now().plusSeconds(300)))
                        .hasErrors())
                .isTrue();
        assertThat(validar(jwt(null, List.of(AUDIENCIA), Instant.now().plusSeconds(300)))
                        .hasErrors())
                .isTrue();
    }

    /** Caso: un token vencido se rechaza (más allá del margen de reloj de 60 s de Spring Security). */
    @Test
    void tokenVencidoSeRechaza() {
        assertThat(validar(jwt(EMISOR, List.of(AUDIENCIA), Instant.now().minusSeconds(3600)))
                        .hasErrors())
                .isTrue();
    }

    /** Caso: crear el decodificador NO consulta al emisor (ni siquiera con uno inalcanzable): la consulta es perezosa. */
    @Test
    void crearElDecodificadorNoConsultaAlEmisor() {
        JwtDecoder decodificador = ValidadoresJwt.decodificador("http://127.0.0.1:9/realms/pilot", AUDIENCIA);

        assertThat(decodificador).isNotNull();
    }
}

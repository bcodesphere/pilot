package com.bcodesphere.pilot.plataforma.infraestructura;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Fábrica del validador y del decodificador de los tokens de acceso de Keycloak (ADR-008, CLAUDE.md 14.1).
 * El token debe estar vigente, venir del emisor configurado y llevar la audiencia de la API ({@code pilot-api}).
 */
public final class ValidadoresJwt {

    private static final Logger LOG = LoggerFactory.getLogger(ValidadoresJwt.class);

    private ValidadoresJwt() {}

    /**
     * Validador que exige vigencia (exp/nbf), emisor y audiencia.
     *
     * @param emisor emisor esperado en el claim {@code iss}
     * @param audiencia audiencia que debe figurar en el claim {@code aud}
     * @return validador combinado; cualquiera de las tres reglas que falle rechaza el token
     */
    public static OAuth2TokenValidator<Jwt> validador(String emisor, String audiencia) {
        // 1. Vigencia y emisor: el validador por defecto de Spring Security con el emisor fijado
        OAuth2TokenValidator<Jwt> vigenciaYEmisor = JwtValidators.createDefaultWithIssuer(emisor);
        // 2. Audiencia: aud es una lista (Nimbus la normaliza); el token debe traer la de esta API. Un token emitido
        //    para otro cliente del mismo realm no sirve aunque su firma y emisor sean válidos
        OAuth2TokenValidator<Jwt> conAudiencia = new JwtClaimValidator<Object>(
                JwtClaimNames.AUD, aud -> aud instanceof Collection<?> c && c.contains(audiencia));
        return new DelegatingOAuth2TokenValidator<>(vigenciaYEmisor, conAudiencia);
    }

    /**
     * Decodificador que obtiene la configuración del emisor en el PRIMER USO y no al arrancar, así la aplicación
     * arranca aunque Keycloak no esté levantado. Si el primer intento falla, se reintenta en la siguiente petición.
     *
     * @param emisor URL del emisor OIDC (de {@code PILOT_OIDC_ISSUER})
     * @param audiencia audiencia exigida
     * @return decodificador perezoso con firma RS256 y las validaciones de {@link #validador}
     */
    public static JwtDecoder decodificador(String emisor, String audiencia) {
        JwtDecoder perezoso = new SupplierJwtDecoder(() -> {
            // Descubre el jwks_uri del emisor (llamada de red) y fija RS256, el algoritmo de firma de Keycloak
            NimbusJwtDecoder decodificador = NimbusJwtDecoder.withIssuerLocation(emisor)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decodificador.setJwtValidator(validador(emisor, audiencia));
            return decodificador;
        });
        // Si el emisor no responde, Spring Security lanza una excepción de inicialización que NO es de autenticación y
        // terminaría en 500. Se traduce a un token inválido para que la petición termine en 401 PLT-009: nunca se
        // acepta un token que no se pudo validar, y el cliente reintenta al iniciar sesión de nuevo
        return token -> {
            try {
                return perezoso.decode(token);
            } catch (JwtDecoderInitializationException e) {
                LOG.warn("No se pudo consultar la configuración del emisor OIDC; el token no se puede validar");
                throw new BadJwtException("No se pudo validar el token", e);
            }
        };
    }
}

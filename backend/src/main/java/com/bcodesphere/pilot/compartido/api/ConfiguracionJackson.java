package com.bcodesphere.pilot.compartido.api;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.MutableCoercionConfig;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Personaliza el {@link JsonMapper.Builder} que autoconfigura Spring Boot (Jackson 3) para que un número o un
 * booleano JSON nunca se conviertan silenciosamente en un campo de texto ({@link String}).
 *
 * <p>Por qué (ADR-013 y CLAUDE.md 8.4 PLT-001): el contrato declara los montos y demás valores sensibles como
 * cadena decimal (p. ej. {@code MontoEntrada} en {@code api-spec/}), nunca como número JSON. Por defecto, Jackson 3
 * coacciona un escalar numérico o booleano a texto sin avisar (p. ej. {@code 100.00} se convertiría en {@code
 * "100.0"}, que además puede seguir cumpliendo el patrón esperado y esconder el error). Al declarar esa coerción
 * como {@link CoercionAction#Fail} para el tipo lógico textual, la lectura lanza una excepción de Jackson que
 * {@link ManejadorErroresGlobal#jsonIlegible} ya traduce a 400 {@code PLT-001} para cualquier cuerpo ilegible, sin
 * exponer detalles internos de Jackson en la respuesta.
 *
 * <p>El alcance es todo cuerpo JSON de la API, no solo el Libro Diario: por ejemplo, también protege el futuro
 * webhook de n8n (que además valida su propio esquema JSON antes de interpretarlo, CLAUDE.md 1.1.14). Como
 * {@code @Component}, Spring Boot invoca {@link #customize} al construir su {@link JsonMapper.Builder}
 * autoconfigurado: esta clase lo ajusta, no lo reemplaza, así que conserva el resto de la configuración (incluida
 * la serialización de montos como cadena de {@link ModuloJacksonDinero}).
 */
@Component
public class ConfiguracionJackson implements JsonMapperBuilderCustomizer {

    /**
     * Agrega, sobre el builder que ya trae Spring Boot, la coerción a texto: un entero, un decimal o un booleano
     * JSON dejan de convertirse en cadena y fallan la lectura.
     *
     * @param builder builder autoconfigurado por Spring Boot; se modifica en el mismo objeto (no se reemplaza)
     */
    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.withCoercionConfig(LogicalType.Textual, ConfiguracionJackson::rechazarEscalaresNoTextuales);
    }

    /** Marca como fallo los tres orígenes escalares que no son ya una cadena JSON. */
    private static void rechazarEscalaresNoTextuales(MutableCoercionConfig coercion) {
        // 1. Entero (p. ej. 100) -> antes se convertía en "100"
        coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        // 2. Decimal (p. ej. 100.00) -> antes se convertía en "100.0"; es el caso de un monto (ADR-013)
        coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        // 3. Booleano (p. ej. true) -> antes se convertía en "true"
        coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }
}

package com.bcodesphere.pilot.compartido.api;

import com.bcodesphere.pilot.compartido.Dinero;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Módulo Jackson 3 que serializa {@link Dinero} como cadena decimal y solo lo lee desde cadena (ADR-013).
 * Como {@code @Component}, Spring Boot lo registra en el mapeador de la API.
 */
@Component
public class ModuloJacksonDinero extends SimpleModule {

    private static final long serialVersionUID = 1L;

    /** Registra el serializador y el deserializador de {@link Dinero}. */
    public ModuloJacksonDinero() {
        super("ModuloJacksonDinero");
        addSerializer(Dinero.class, new Escritor());
        addDeserializer(Dinero.class, new Lector());
    }

    /** Escribe {@code "123.45"}. */
    static final class Escritor extends ValueSerializer<Dinero> {
        @Override
        public void serialize(Dinero valor, JsonGenerator generador, SerializationContext contexto) {
            generador.writeString(valor.toString());
        }
    }

    /** Lee solo cadenas: un número JSON pasaría por {@code double} y perdería exactitud (CLAUDE.md 1.2.2). */
    static final class Lector extends ValueDeserializer<Dinero> {
        @Override
        public Dinero deserialize(JsonParser parser, DeserializationContext contexto) {
            // 1. Rechaza números, booleanos, objetos, etc.
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return contexto.reportInputMismatch(Dinero.class, "El monto debe enviarse como cadena decimal");
            }
            // 2. Aplica el patrón del contrato; un texto inválido se informa como error de entrada
            try {
                return Dinero.de(parser.getString());
            } catch (IllegalArgumentException e) {
                return contexto.reportInputMismatch(Dinero.class, "Monto inválido: %s", e.getMessage());
            }
        }
    }
}

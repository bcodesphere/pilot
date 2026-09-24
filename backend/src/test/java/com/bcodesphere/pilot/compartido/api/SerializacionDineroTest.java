package com.bcodesphere.pilot.compartido.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.Dinero;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Serialización Jackson 3 de {@link Dinero} como cadena decimal (ADR-013). */
class SerializacionDineroTest {

    private final JsonMapper mapper =
            JsonMapper.builder().addModule(new ModuloJacksonDinero()).build();

    /** Caso: se escribe como cadena con 2 decimales y se lee de vuelta. */
    @Test
    void escribeYLeeCadena() {
        assertThat(mapper.writeValueAsString(Dinero.de("123.45"))).isEqualTo("\"123.45\"");
        assertThat(mapper.readValue("\"123.45\"", Dinero.class)).isEqualTo(Dinero.de("123.45"));
    }

    /** Caso: un número JSON se rechaza porque pasaría por double. */
    @Test
    void rechazaNumerosJson() {
        assertThatThrownBy(() -> mapper.readValue("123.45", Dinero.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("\"1.005\"", Dinero.class)).isInstanceOf(Exception.class);
    }
}

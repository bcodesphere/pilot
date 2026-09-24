package com.bcodesphere.pilot.compartido.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.Dinero;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comprueba que {@link ModuloJacksonDinero} queda registrado en el mapeador real que construye Spring Boot
 * (autoconfiguración de Jackson), no solo en uno armado a mano. Sin base de datos ni servidor web.
 */
class RegistroJacksonDineroTest {

    /** Record de prueba con un campo de dinero. */
    record Linea(Dinero monto) {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(ModuloJacksonDinero.class);

    /** Caso: el monto se escribe como cadena decimal y un número JSON se rechaza (ADR-013). */
    @Test
    void elMapeadorDeSpringUsaElModulo() {
        runner.run(contexto -> {
            JsonMapper mapper = contexto.getBean(JsonMapper.class);
            assertThat(mapper.writeValueAsString(new Linea(Dinero.de("123.45"))))
                    .isEqualTo("{\"monto\":\"123.45\"}");
            assertThatThrownBy(() -> mapper.readValue("{\"monto\": 123.45}", Linea.class))
                    .isInstanceOf(DatabindException.class)
                    .hasMessageContaining("cadena decimal");
        });
    }
}

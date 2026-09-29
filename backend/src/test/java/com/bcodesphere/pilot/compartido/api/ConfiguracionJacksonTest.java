package com.bcodesphere.pilot.compartido.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comprueba que {@link ConfiguracionJackson} queda registrada en el mapeador real que construye Spring Boot
 * (autoconfiguración de Jackson 3), no solo en uno armado a mano. Sin base de datos ni servidor web, igual que
 * {@link RegistroJacksonDineroTest}.
 */
class ConfiguracionJacksonTest {

    /** Record de prueba con un campo de texto cualquiera (representa, por ejemplo, un {@code MontoEntrada}). */
    record Cuerpo(String texto) {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(ConfiguracionJackson.class);

    /** Caso base (regla CON-007/PLT-001, ADR-013): un entero JSON en un campo de texto no se vuelve cadena. */
    @Test
    void unEnteroJsonEnUnCampoDeTextoFallaLaLectura() {
        runner.run(contexto -> {
            JsonMapper mapper = contexto.getBean(JsonMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"texto\": 100}", Cuerpo.class))
                    .isInstanceOf(DatabindException.class);
        });
    }

    /** Caso real del defecto F3-07: un decimal JSON (un monto, p. ej. 100.00) también falla en vez de "100.0". */
    @Test
    void unDecimalJsonEnUnCampoDeTextoFallaLaLectura() {
        runner.run(contexto -> {
            JsonMapper mapper = contexto.getBean(JsonMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"texto\": 100.00}", Cuerpo.class))
                    .isInstanceOf(DatabindException.class);
        });
    }

    /** Caso: un booleano JSON en un campo de texto también falla. */
    @Test
    void unBooleanoJsonEnUnCampoDeTextoFallaLaLectura() {
        runner.run(contexto -> {
            JsonMapper mapper = contexto.getBean(JsonMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"texto\": true}", Cuerpo.class))
                    .isInstanceOf(DatabindException.class);
        });
    }

    /** Caso: una cadena JSON normal se sigue leyendo igual que antes; la regla de negocio la valida después. */
    @Test
    void unaCadenaJsonSigueLeyendoseNormal() {
        runner.run(contexto -> {
            JsonMapper mapper = contexto.getBean(JsonMapper.class);
            assertThat(mapper.readValue("{\"texto\": \"100.00\"}", Cuerpo.class).texto())
                    .isEqualTo("100.00");
        });
    }
}

package com.bcodesphere.pilot.contabilidad.dominio.iva;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Casos dorados de {@link CalculadoraIva}. Fuente: CLAUDE.md 11.1 (fórmulas y cuatro casos obligatorios con t = 13 %),
 * redondeo HALF_UP a 2 decimales (regla 1.1.2). La tasa se pasa como dato de prueba: el código de producción la lee
 * de {@code tasa_impuesto}.
 */
class CalculadoraIvaTest {

    private static final BigDecimal TASA = new BigDecimal("0.1300");

    /** Los cuatro casos dorados de CLAUDE.md 11.1 más algunos bordes de redondeo y de montos mínimos. */
    @ParameterizedTest(name = "{0} {1} → base {2} + IVA {3}")
    @CsvSource({
        // modo, monto, base esperada, IVA esperado
        "CON_IVA, 113.00, 100.00, 13.00", // caso dorado 1: el total incluye el IVA
        "SIN_IVA, 100.00, 100.00, 13.00", // caso dorado 2: el IVA se suma a la base
        "CON_IVA, 5.00, 4.42, 0.58", // caso dorado 3: 5 × 0.13 / 1.13 = 0.5752 → 0.58 (HALF_UP)
        "SIN_IVA, 4.42, 4.42, 0.57", // caso dorado 4: 4.42 × 0.13 = 0.5746 → 0.57
        "CON_IVA, 0.01, 0.01, 0.00", // el IVA de un centavo redondea a cero y la base conserva el centavo
        "SIN_IVA, 0.04, 0.04, 0.01", // 0.04 × 0.13 = 0.0052 → 0.01
        "SIN_IVA, 0.03, 0.03, 0.00" // 0.03 × 0.13 = 0.0039 → 0.00
    })
    void separaBaseEIvaSegunElModo(ModoPrecio modo, String monto, String base, String iva) {
        SeparacionIva resultado = CalculadoraIva.separar(Dinero.de(monto), modo, TASA);

        assertThat(resultado.base()).isEqualTo(Dinero.de(base));
        assertThat(resultado.iva()).isEqualTo(Dinero.de(iva));
    }

    /** Invariante de CON_IVA: base + IVA reproduce exactamente el monto ingresado, sin perder centavos. */
    @ParameterizedTest
    @CsvSource({"1.13", "22.60", "999.99", "12345.67", "0.58"})
    void enConIvaLaBaseMasElIvaEsElMonto(String monto) {
        SeparacionIva r = CalculadoraIva.separar(Dinero.de(monto), ModoPrecio.CON_IVA, TASA);

        assertThat(r.base().sumar(r.iva())).isEqualTo(Dinero.de(monto));
    }
}

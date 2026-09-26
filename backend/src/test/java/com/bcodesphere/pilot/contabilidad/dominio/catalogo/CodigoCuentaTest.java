package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas del código de cuenta. Fuente: CLAUDE.md 10.2 (clases 1 a 5, niveles por longitud 1, 2, 4, 6 y 8, naturaleza
 * por defecto) y ADR-035 (CON-010 y CON-015).
 */
class CodigoCuentaTest {

    /** Cada longitud válida corresponde a un nivel: 1 clase, 2 grupo, 4 cuenta, 6 subcuenta, 8 detalle. */
    @ParameterizedTest
    @CsvSource({"1,1", "11,2", "1101,3", "110101,4", "11010101,5"})
    void laLongitudDelCodigoDefineElNivel(String codigo, int nivelEsperado) {
        assertThat(CodigoCuenta.de(codigo).nivel()).isEqualTo(nivelEsperado);
    }

    /** CON-010: un primer dígito fuera de 1 a 5 (la clase 6 es del cierre anual, el 0 no existe) se rechaza. */
    @ParameterizedTest
    @ValueSource(strings = {"6", "60", "6101", "0", "01", "9", "A101", "١١٠١"})
    void unaClaseFueraDeUnoACincoDaCon010(String codigo) {
        assertThatThrownBy(() -> CodigoCuenta.de(codigo)).isInstanceOfSatisfying(ExcepcionContabilidad.class, e -> {
            assertThat(e.codigo()).isEqualTo("CON-010");
            assertThat(e.estadoHttp()).isEqualTo(422);
        });
    }

    /** CON-015: longitud 3, 5, 7 o 9 (no es un nivel), caracteres no numéricos y texto vacío o nulo. */
    @ParameterizedTest
    @ValueSource(strings = {"110", "11010", "1101010", "110101011", "11A1", "1 01", "1١٠١", ""})
    void unaLongitudOCaracterInvalidosDanCon015(String codigo) {
        assertThatThrownBy(() -> CodigoCuenta.de(codigo)).isInstanceOfSatisfying(ExcepcionContabilidad.class, e -> {
            assertThat(e.codigo()).isEqualTo("CON-015");
            assertThat(e.estadoHttp()).isEqualTo(422);
        });
    }

    /** Un código nulo es un código no válido (CON-015), no un error interno. */
    @Test
    void unCodigoNuloDaCon015() {
        assertThatThrownBy(() -> CodigoCuenta.de(null))
                .isInstanceOfSatisfying(
                        ExcepcionContabilidad.class, e -> assertThat(e.codigo()).isEqualTo("CON-015"));
    }

    /** Naturaleza por defecto: deudora en las clases 1 y 4; acreedora en 2, 3 y 5 (CLAUDE.md 10.2). */
    @ParameterizedTest
    @CsvSource({
        "1101,DEUDORA",
        "2102,ACREEDORA",
        "3101,ACREEDORA",
        "4101,DEUDORA",
        "5101,ACREEDORA",
        "1,DEUDORA",
        "5,ACREEDORA"
    })
    void laNaturalezaPorDefectoDependeDeLaClase(String codigo, NaturalezaCuenta esperada) {
        assertThat(CodigoCuenta.de(codigo).naturalezaPorDefecto()).isEqualTo(esperada);
    }

    /** La clase es el primer dígito. */
    @Test
    void laClaseEsElPrimerDigito() {
        assertThat(CodigoCuenta.de("41010203").clase()).isEqualTo(4);
    }

    /** El código del padre es el prefijo con la longitud del nivel anterior; una clase no tiene padre. */
    @ParameterizedTest
    @CsvSource({"11,1", "1101,11", "110101,1101", "11010101,110101"})
    void elPadreEsperadoEsElPrefijoDelNivelAnterior(String codigo, String padre) {
        assertThat(CodigoCuenta.de(codigo).codigoPadreEsperado()).contains(CodigoCuenta.de(padre));
    }

    /** Una clase (nivel 1) no tiene padre. */
    @Test
    void unaClaseNoTienePadre() {
        assertThat(CodigoCuenta.de("1").codigoPadreEsperado()).isEmpty();
    }
}

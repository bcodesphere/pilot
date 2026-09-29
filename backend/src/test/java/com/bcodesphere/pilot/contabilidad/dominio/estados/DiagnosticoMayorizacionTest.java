package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.estados.CatalogoDePrueba.Catalogo;
import org.junit.jupiter.api.Test;

/** {@link DiagnosticoMayorizacion}: {@code consistente} depende únicamente de si hay diferencias (ADR-018). */
class DiagnosticoMayorizacionTest {

    @Test
    void esConsistenteCuandoNoHayDiferencias() {
        DiagnosticoMayorizacion d = DiagnosticoMayorizacion.de(12, java.util.List.of());

        assertThat(d.consistente()).isTrue();
        assertThat(d.cantidadCuentasRevisadas()).isEqualTo(12);
    }

    @Test
    void noEsConsistenteCuandoHayAlMenosUnaDiferencia() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        DiferenciaMayorizacion diferencia = new DiferenciaMayorizacion(
                catalogo.detalle("11010101"),
                2026,
                3,
                Dinero.de("100.00"),
                Dinero.CERO,
                Dinero.de("90.00"),
                Dinero.CERO);

        DiagnosticoMayorizacion d = DiagnosticoMayorizacion.de(5, java.util.List.of(diferencia));

        assertThat(d.consistente()).isFalse();
        assertThat(d.diferencias()).containsExactly(diferencia);
    }
}

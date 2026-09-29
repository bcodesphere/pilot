package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ResumenIva} y {@link DesgloseIva}: IVA débito y crédito fiscal desglosados por origen y la diferencia
 * estimada (ADR-038 §8). Los valores esperados se calculan a mano.
 */
class ResumenIvaTest {

    private static final ResumenCuenta CUENTA_DEBITO =
            new ResumenCuenta(UUID.randomUUID(), "21020101", "IVA débito fiscal");
    private static final ResumenCuenta CUENTA_CREDITO =
            new ResumenCuenta(UUID.randomUUID(), "11040101", "IVA crédito fiscal");

    /**
     * IVA débito: manual 130.00, reversión -13.00 (una reversión de una venta pequeña) → total 117.00. IVA crédito:
     * manual 45.50 → total 45.50. Diferencia estimada = 117.00 − 45.50 = 71.50.
     */
    @Test
    void desglosaPorOrigenYCalculaLaDiferenciaEstimada() {
        Map<OrigenAsiento, Dinero> debito =
                Map.of(OrigenAsiento.MANUAL, Dinero.de("130.00"), OrigenAsiento.REVERSION, Dinero.de("-13.00"));
        Map<OrigenAsiento, Dinero> credito = Map.of(OrigenAsiento.MANUAL, Dinero.de("45.50"));

        ResumenIva r = ResumenIva.de(2026, 3, CUENTA_DEBITO, CUENTA_CREDITO, debito, credito);

        assertThat(r.ivaDebito().manual()).isEqualTo(Dinero.de("130.00"));
        assertThat(r.ivaDebito().reversion()).isEqualTo(Dinero.de("-13.00"));
        assertThat(r.ivaDebito().n8n()).isEqualTo(Dinero.CERO);
        assertThat(r.ivaDebito().total()).isEqualTo(Dinero.de("117.00"));
        assertThat(r.ivaCredito().total()).isEqualTo(Dinero.de("45.50"));
        assertThat(r.diferenciaEstimada()).isEqualTo(Dinero.de("71.50"));
        assertThat(r.nota()).isEqualTo(ResumenIva.NOTA);
    }

    /** Sin ningún movimiento de IVA en el mes, todo queda en cero y no lanza error. */
    @Test
    void sinMovimientoDeIvaTodoQuedaEnCero() {
        ResumenIva r = ResumenIva.de(2026, 3, CUENTA_DEBITO, CUENTA_CREDITO, Map.of(), Map.of());

        assertThat(r.ivaDebito().total()).isEqualTo(Dinero.CERO);
        assertThat(r.ivaCredito().total()).isEqualTo(Dinero.CERO);
        assertThat(r.diferenciaEstimada()).isEqualTo(Dinero.CERO);
    }
}

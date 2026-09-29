package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Propiedad "saldo = Σ líneas" (CLAUDE.md §10.3 y §15: "tras cualquier secuencia aleatoria de asientos y reversiones,
 * saldo_cuenta_mensual = Σ líneas") con semillas fijas ({@link java.util.Random}, sin jqwik por restricción de
 * alcance). Cada semilla genera de 40 a 60 operaciones sobre cuentas de detalle al azar (excluidas las dos cuentas de
 * IVA configuradas, para que un "lleva IVA" nunca caiga sobre ellas y dispare CON-013 por accidente): asientos de 2 a
 * 6 líneas cuadradas, con y sin IVA, en ambos modos de precio, repartidos en cuatro meses distintos (todos pasados
 * respecto de hoy), y reversiones de asientos elegidos al azar entre los que siguen {@code CONTABILIZADO}. Al final,
 * como dueño, se verifica para toda cuenta y mes de la empresa: {@code saldo_cuenta_mensual.total_debe/total_haber} =
 * Σ de {@code asiento_linea} de esa cuenta y mes, y que no hay una fila de saldo sin líneas ni líneas sin su fila de
 * saldo.
 */
class PropiedadMayorizacionF3IT extends BaseAceptacionF3IT {

    private static final ZoneId ZONA_SV = ZoneId.of("America/El_Salvador");

    /** Fila comparada de {@code saldo_cuenta_mensual} contra la suma real de {@code asiento_linea}. */
    private record FilaSaldo(
            UUID cuentaId,
            int anio,
            int mes,
            BigDecimal totalDebe,
            BigDecimal totalHaber,
            BigDecimal sumaDebe,
            BigDecimal sumaHaber,
            int cantidadLineas) {}

    /**
     * Cinco semillas fijas: cualquier fallo se reproduce exactamente reejecutando la prueba con esa semilla (impresa
     * en el nombre, {@code name = "semilla {0}"}).
     */
    @ParameterizedTest(name = "semilla {0}")
    @ValueSource(longs = {20260315L, 777L, 4242424242L, 9001L, 314159265L})
    void trasOperacionesAleatoriasElSaldoEsLaSumaDeLasLineas(long semilla) throws Exception {
        Random azar = new Random(semilla);
        Sesion s = sesionConContabilidad();

        List<UUID> cuentas = cuentasDeDetalleSinIva(s);
        List<LocalDate> meses = mesesPasados();
        List<String> vigentes = new ArrayList<>();
        LocalDate fechaReversion = LocalDate.now(ZONA_SV).minusMonths(2).withDayOfMonth(10);

        int operaciones = 40 + azar.nextInt(21); // 40 a 60
        int contadorNuevos = 0;
        for (int i = 0; i < operaciones; i++) {
            boolean revertir = !vigentes.isEmpty() && azar.nextInt(4) == 0;
            if (revertir) {
                String objetivo = vigentes.remove(azar.nextInt(vigentes.size()));
                postConClave(
                                s,
                                "/contabilidad/asientos/" + objetivo + "/reversion",
                                "rev-" + semilla + "-" + i,
                                "{\"fecha\":\"" + fechaReversion + "\"}")
                        .andExpect(status().isCreated());
            } else {
                LocalDate fecha = meses.get(contadorNuevos % meses.size());
                contadorNuevos++;
                String cuerpo = asientoAleatorio(azar, cuentas, fecha);
                String id = leer(
                        postConClave(s, "/contabilidad/asientos", "asi-" + semilla + "-" + i, cuerpo)
                                .andExpect(status().isCreated()),
                        "$.id");
                vigentes.add(id);
            }
        }

        verificarInvariante(s);
    }

    // ---------------------------------------------------------------------------------------------- generación

    /** Cuentas de detalle de la empresa, sin las dos configuradas para IVA (evita CON-013 por accidente). */
    private List<UUID> cuentasDeDetalleSinIva(Sesion s) throws Exception {
        String ivaDebito = leer(get(s, "/contabilidad/configuracion"), "$.cuentaIvaDebito.codigo");
        String ivaCredito = leer(get(s, "/contabilidad/configuracion"), "$.cuentaIvaCredito.codigo");
        return duenio.sql("SELECT id FROM cuenta_contable WHERE empresa_id = ? AND acepta_movimientos"
                        + " AND activa AND codigo NOT IN (?, ?) ORDER BY codigo")
                .params(s.empresa(), ivaDebito, ivaCredito)
                .query(UUID.class)
                .list();
    }

    /** Cuatro meses distintos, todos pasados respecto de hoy en hora de El Salvador (ninguna fecha futura, CON-007). */
    private List<LocalDate> mesesPasados() {
        LocalDate hoy = LocalDate.now(ZONA_SV);
        List<LocalDate> meses = new ArrayList<>();
        for (int atras = 6; atras >= 3; atras--) {
            meses.add(hoy.minusMonths(atras).withDayOfMonth(10));
        }
        return meses;
    }

    /**
     * Asiento aleatorio de 2 a 6 líneas cuadradas, con y sin IVA en ambos modos de precio. Por simplicidad (y
     * siguiendo el mismo lado que los ejemplos de CLAUDE.md §11.2) la línea "lleva IVA" de un asiento {@code SIN_IVA}
     * siempre está en el lado Debe, y la de un asiento {@code CON_IVA} siempre en el Haber: así el ajuste de totales
     * es directo. {@code SIN_IVA} agrega una línea de IVA nueva sobre el monto ya ingresado (no reparte el monto como
     * {@code CON_IVA}), así que el lado opuesto debe generarse ya inflado por ese IVA para seguir cuadrando; se
     * calcula con la misma fórmula HALF_UP que {@code CalculadoraIva} (tasa del 13 %, ADR-034).
     */
    private String asientoAleatorio(Random azar, List<UUID> cuentas, LocalDate fecha) {
        int lineas = 2 + azar.nextInt(5); // 2 a 6
        int debeCount = (lineas + 1) / 2;
        int haberCount = lineas - debeCount;

        long[] debe = new long[debeCount];
        long totalDebeBase = 0;
        for (int i = 0; i < debeCount; i++) {
            debe[i] = 100 + azar.nextInt(99_900); // 1.00 a 999.99
            totalDebeBase += debe[i];
        }

        boolean conIva = azar.nextInt(10) < 4; // ~40%
        String modo = null;
        int lineaConIvaDebe = -1;
        int lineaConIvaHaber = -1;
        long objetivoHaber = totalDebeBase;
        if (conIva) {
            modo = azar.nextBoolean() ? "CON_IVA" : "SIN_IVA";
            if ("SIN_IVA".equals(modo)) {
                // La línea marcada va en el Debe; el IVA que se le agrega infla lo que debe sumar el Haber
                lineaConIvaDebe = azar.nextInt(debeCount);
                long ivaCents = (debe[lineaConIvaDebe] * 13 + 50) / 100; // HALF_UP en centavos, como CalculadoraIva
                objetivoHaber = totalDebeBase + ivaCents;
            }
        }

        long[] haber = dividir(objetivoHaber, haberCount, azar);
        if ("CON_IVA".equals(modo)) {
            // CON_IVA reparte el propio monto en base + IVA sin cambiar el total de su lado: no hace falta inflar nada
            lineaConIvaHaber = azar.nextInt(haberCount);
        }

        List<String> partes = new ArrayList<>();
        for (int i = 0; i < debeCount; i++) {
            UUID cuenta = cuentas.get(azar.nextInt(cuentas.size()));
            partes.add(lineaJson(cuenta, BigDecimal.valueOf(debe[i], 2), BigDecimal.ZERO, i == lineaConIvaDebe));
        }
        for (int i = 0; i < haberCount; i++) {
            UUID cuenta = cuentas.get(azar.nextInt(cuentas.size()));
            partes.add(lineaJson(cuenta, BigDecimal.ZERO, BigDecimal.valueOf(haber[i], 2), i == lineaConIvaHaber));
        }

        return "{\"fecha\":\"" + fecha + "\",\"concepto\":\"Operación aleatoria de la propiedad de mayorización\""
                + (modo == null ? "" : ",\"modoPrecio\":\"" + modo + "\"")
                + ",\"lineas\":[" + String.join(",", partes) + "]}";
    }

    /** Reparte {@code total} en {@code partes} piezas positivas (mínimo 1 centavo cada una), al azar. */
    private static long[] dividir(long total, int partes, Random azar) {
        long[] piezas = new long[partes];
        long restante = total;
        for (int i = 0; i < partes - 1; i++) {
            long maximo = restante - (partes - 1 - i); // deja al menos 1 centavo para cada pieza que falta
            long pieza = 1 + (maximo > 1 ? (long) (azar.nextDouble() * (maximo - 1)) : 0);
            piezas[i] = pieza;
            restante -= pieza;
        }
        piezas[partes - 1] = restante;
        return piezas;
    }

    private static String lineaJson(UUID cuenta, BigDecimal debe, BigDecimal haber, boolean llevaIva) {
        return "{\"cuentaId\":\"" + cuenta + "\",\"debe\":\"" + debe + "\",\"haber\":\"" + haber + "\",\"llevaIva\":"
                + llevaIva + "}";
    }

    // -------------------------------------------------------------------------------------------- verificación

    /** Verifica, como dueño, que cada fila de saldo coincide con la suma real de sus líneas y que no sobran ni faltan filas. */
    private void verificarInvariante(Sesion s) {
        List<FilaSaldo> filas = duenio.sql("""
                        SELECT s.cuenta_id AS cuenta_id, s.anio AS anio, s.mes AS mes,
                               s.total_debe AS total_debe, s.total_haber AS total_haber,
                               COALESCE((SELECT SUM(al.debe) FROM asiento_linea al
                                          WHERE al.empresa_id = s.empresa_id AND al.cuenta_id = s.cuenta_id
                                            AND EXTRACT(YEAR FROM al.fecha) = s.anio AND EXTRACT(MONTH FROM al.fecha) = s.mes), 0) AS suma_debe,
                               COALESCE((SELECT SUM(al.haber) FROM asiento_linea al
                                          WHERE al.empresa_id = s.empresa_id AND al.cuenta_id = s.cuenta_id
                                            AND EXTRACT(YEAR FROM al.fecha) = s.anio AND EXTRACT(MONTH FROM al.fecha) = s.mes), 0) AS suma_haber,
                               (SELECT COUNT(*) FROM asiento_linea al
                                 WHERE al.empresa_id = s.empresa_id AND al.cuenta_id = s.cuenta_id
                                   AND EXTRACT(YEAR FROM al.fecha) = s.anio AND EXTRACT(MONTH FROM al.fecha) = s.mes) AS cantidad_lineas
                          FROM saldo_cuenta_mensual s
                         WHERE s.empresa_id = ?
                        """)
                .param(s.empresa())
                .query((rs, i) -> new FilaSaldo(
                        (UUID) rs.getObject("cuenta_id"),
                        rs.getInt("anio"),
                        rs.getInt("mes"),
                        rs.getBigDecimal("total_debe"),
                        rs.getBigDecimal("total_haber"),
                        rs.getBigDecimal("suma_debe"),
                        rs.getBigDecimal("suma_haber"),
                        rs.getInt("cantidad_lineas")))
                .list();

        assertThat(filas)
                .as("hay al menos una cuenta con saldo tras las operaciones")
                .isNotEmpty();
        for (FilaSaldo f : filas) {
            assertThat(f.totalDebe())
                    .as("cuenta %s, %d-%02d: total_debe = Σ líneas.debe", f.cuentaId(), f.anio(), f.mes())
                    .isEqualByComparingTo(f.sumaDebe());
            assertThat(f.totalHaber())
                    .as("cuenta %s, %d-%02d: total_haber = Σ líneas.haber", f.cuentaId(), f.anio(), f.mes())
                    .isEqualByComparingTo(f.sumaHaber());
            assertThat(f.cantidadLineas())
                    .as("cuenta %s, %d-%02d: no hay fila de saldo sin líneas", f.cuentaId(), f.anio(), f.mes())
                    .isGreaterThan(0);
        }

        // Complemento: ninguna cuenta y mes con líneas se quedó sin su fila de saldo
        List<UUID> faltantes = duenio.sql("""
                        SELECT al.cuenta_id AS cuenta_id
                          FROM asiento_linea al
                         WHERE al.empresa_id = ?
                         GROUP BY al.cuenta_id, EXTRACT(YEAR FROM al.fecha), EXTRACT(MONTH FROM al.fecha)
                        EXCEPT
                        SELECT cuenta_id FROM saldo_cuenta_mensual WHERE empresa_id = ?
                        """)
                .params(s.empresa(), s.empresa())
                .query(UUID.class)
                .list();
        assertThat(faltantes)
                .as("cuentas y meses con líneas pero sin fila de saldo")
                .isEmpty();
    }
}

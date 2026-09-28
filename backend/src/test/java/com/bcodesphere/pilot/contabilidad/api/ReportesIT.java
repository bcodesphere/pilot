package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de integración de los reportes contables (F4-03) contra PostgreSQL real como {@code pilot_app}. Fuente:
 * CLAUDE.md 10.3, 10.4 y 10.5, ADR-016, ADR-018, ADR-037 y ADR-038, y los criterios de aceptación de F4 del plan de
 * trabajo. Los asientos se siembran por la API (con {@link LibroDiarioIT#asiento} y {@link LibroDiarioIT#linea});
 * las fechas son fijas y pasadas para no depender del día en que corran las pruebas.
 */
class ReportesIT extends BaseContabilidadIT {

    private static final String JUN_2025 = "2025-06-15";
    private static final String NOV_2025 = "2025-11-20";
    private static final String NOV_2025_REVERSION = "2025-11-25";
    private static final String FEB_2026_A = "2026-02-10";
    private static final String FEB_2026_B = "2026-02-11";
    private static final String FEB_2026_B_REVERSION = "2026-02-12";
    private static final String AGO_2026_A = "2026-08-05";
    private static final String AGO_2026_B = "2026-08-06";

    /** Cuenta de Caja del catálogo base (Regla EFECTIVO, CLAUDE.md 12.5). */
    private static final String CAJA = "11010101";

    /** Cuenta de Ventas gravadas del catálogo base (Regla VENTAS_GRAVADAS). */
    private static final String VENTAS_GRAVADAS = "51010101";

    /** Cuenta de Gastos de administración del catálogo base. */
    private static final String GASTOS_ADMIN = "42020101";

    /** Cuenta del grupo 44 (Impuesto sobre la renta), agregada por la migración V15 (ADR-037). */
    private static final String IMPUESTO_RENTA = "44010101";

    /** Registra un asiento con la clave dada y devuelve su id. */
    private String registrar(Sesion s, String clave, String cuerpo) throws Exception {
        return leer(postConClave(s, "/contabilidad/asientos", clave, cuerpo).andExpect(status().isCreated()), "$.id");
    }

    /** Revierte un asiento en la fecha dada y devuelve el id de la reversión. */
    private String revertir(Sesion s, String asientoId, String clave, String fecha) throws Exception {
        return leer(
                postConClave(
                                s,
                                "/contabilidad/asientos/" + asientoId + "/reversion",
                                clave,
                                "{\"fecha\":\"" + fecha + "\"}")
                        .andExpect(status().isCreated()),
                "$.id");
    }

    /**
     * Siembra el escenario de dos años con IVA y una reversión que se usa en varias pruebas:
     * <pre>
     * 2025-06-15  Caja 113.00 D / Ventas gravadas 113.00 H (IVA)  → Ventas 100.00 + IVA débito 13.00
     * 2025-11-20  Gastos admin 40.00 D / Caja 40.00 H
     * 2025-11-25  Reversión del asiento anterior (se anula por completo dentro de 2025)
     * 2026-02-10  Caja 226.00 D / Ventas gravadas 226.00 H (IVA) → Ventas 200.00 + IVA débito 26.00
     * 2026-02-11  Caja 22.60 D / Ventas gravadas 22.60 H (IVA)   → Ventas 20.00 + IVA débito 2.60
     * 2026-02-12  Reversión del asiento anterior (queda un origen REVERSION en el resumen de IVA de febrero)
     * 2026-08-05  Gastos admin 60.00 D / Caja 60.00 H
     * 2026-08-06  Impuesto sobre la renta 30.00 D / Caja 30.00 H
     * </pre>
     */
    private Sesion sembrarEscenario() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        JUN_2025,
                        "CON_IVA",
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "113.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "113.00", true)));
        String a2 = registrar(
                s,
                "k2",
                LibroDiarioIT.asiento(
                        NOV_2025,
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), GASTOS_ADMIN), "40.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "0", "40.00", false)));
        revertir(s, a2, "k2-rev", NOV_2025_REVERSION);
        registrar(
                s,
                "k3",
                LibroDiarioIT.asiento(
                        FEB_2026_A,
                        "CON_IVA",
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "226.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "226.00", true)));
        String a6 = registrar(
                s,
                "k6",
                LibroDiarioIT.asiento(
                        FEB_2026_B,
                        "CON_IVA",
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "22.60", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "22.60", true)));
        revertir(s, a6, "k6-rev", FEB_2026_B_REVERSION);
        registrar(
                s,
                "k4",
                LibroDiarioIT.asiento(
                        AGO_2026_A,
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), GASTOS_ADMIN), "60.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "0", "60.00", false)));
        registrar(
                s,
                "k5",
                LibroDiarioIT.asiento(
                        AGO_2026_B,
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), IMPUESTO_RENTA), "30.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "0", "30.00", false)));
        return s;
    }

    // -------------------------------------------------------------------------------------------------- Mayor

    /**
     * Criterio F4: Mayor de una cuenta de detalle. Caja en 2026: saldo inicial 113.00 (D, de 2025), 5 líneas
     * (10, 11 y 12/02, 05 y 06/08) y saldo final 113 + 226 + 22.60 - 22.60 - 60 - 30 = 249.00 (D). El Mayor de la
     * cuenta padre "1101" (única hija con movimiento: Caja general) da el mismo resultado (ADR-038 §5).
     */
    @Test
    void mayorDeUnaCuentaDeDetalleYDeSuCuentaPadreCoinciden() throws Exception {
        Sesion s = sembrarEscenario();
        UUID caja = cuentaId(s.empresa(), CAJA);
        UUID padre = cuentaId(s.empresa(), "1101");

        get(s, "/contabilidad/mayor?cuentaId=" + caja + "&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial.monto").value("113.00"))
                .andExpect(jsonPath("$.saldoInicial.lado").value("DEUDOR"))
                .andExpect(jsonPath("$.movimientos.length()").value(5))
                .andExpect(jsonPath("$.saldoFinal.monto").value("249.00"))
                .andExpect(jsonPath("$.saldoFinal.lado").value("DEUDOR"));

        get(s, "/contabilidad/mayor?cuentaId=" + padre + "&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial.monto").value("113.00"))
                .andExpect(jsonPath("$.movimientos.length()").value(5))
                .andExpect(jsonPath("$.saldoFinal.monto").value("249.00"))
                // Cada línea identifica su propia cuenta de detalle (ADR-038 §5)
                .andExpect(jsonPath("$.movimientos[0].cuenta.codigo").value(CAJA));
    }

    // ------------------------------------------------------------------------------------------------ Balanza

    /** Criterio F4: la Balanza de un rango con movimiento siempre cuadra, porque cada asiento ya cuadra (CON-005). */
    @Test
    void laBalanzaDelAnioCuadra() throws Exception {
        Sesion s = sembrarEscenario();

        var respuesta = get(s, "/contabilidad/balanza?desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk());
        String totalDebe = leer(respuesta, "$.totalDebe");
        String totalHaber = leer(respuesta, "$.totalHaber");
        String totalDeudores = leer(respuesta, "$.totalSaldosDeudores");
        String totalAcreedores = leer(respuesta, "$.totalSaldosAcreedores");

        assertThat(totalDebe).isEqualTo(totalHaber);
        assertThat(totalDeudores).isEqualTo(totalAcreedores);
        assertThat((Boolean) leer(respuesta, "$.cuadra")).isTrue();
    }

    // ------------------------------------------------------------------------------------------------ Estados

    /**
     * Criterios F4: el Estado de Resultados separa el grupo 44 de los costos y gastos, y su utilidad del ejercicio
     * es igual a la del Estado de Situación Financiera al 31/12 (mismo período, sin cierre contable). Ingresos 2026
     * = 200.00 + 20.00 - 20.00 (la venta de 22.60 revertida se anula) = 200.00; costos 60.00; utilidad antes de
     * impuesto 140.00; impuesto 30.00; utilidad del ejercicio 110.00.
     */
    @Test
    void elEstadoDeResultadosSeparaElGrupo44YCoincideConLaUtilidadDelBalance() throws Exception {
        Sesion s = sembrarEscenario();

        get(s, "/contabilidad/estados/resultados?desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresos.total").value("200.00"))
                .andExpect(jsonPath("$.costosGastos.total").value("60.00"))
                .andExpect(jsonPath("$.utilidadAntesImpuesto").value("140.00"))
                .andExpect(jsonPath("$.impuestoSobreRenta.total").value("30.00"))
                .andExpect(jsonPath("$.utilidadEjercicio").value("110.00"))
                .andExpect(jsonPath("$.leyenda")
                        .value(com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados.LEYENDA));
        // El grupo 44 nunca aparece dentro de costosGastos, aunque el nivel pedido llegue hasta el detalle
        get(s, "/contabilidad/estados/resultados?desde=2026-01-01&hasta=2026-12-31&nivel=5")
                .andExpect(jsonPath("$.costosGastos.filas[?(@.cuenta.codigo == '" + IMPUESTO_RENTA + "')]")
                        .doesNotExist())
                .andExpect(jsonPath("$.impuestoSobreRenta.filas[?(@.cuenta.codigo == '" + IMPUESTO_RENTA + "')]")
                        .exists());

        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-12-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilidadEjercicio").value("110.00"))
                .andExpect(jsonPath("$.comprobacion.cuadra").value(true))
                .andExpect(jsonPath("$.comprobacion.diferencia").value("0.00"));
    }

    // ------------------------------------------------------------------------------------------ reflejo inmediato

    /** Criterio F4: un asiento guardado aparece en la balanza en la petición siguiente, sin ningún caché. */
    @Test
    void unAsientoNuevoSeReflejaInmediatamenteEnLaBalanza() throws Exception {
        Sesion s = sesionConContabilidad();

        get(s, "/contabilidad/balanza?desde=2026-05-01&hasta=2026-05-31")
                .andExpect(jsonPath("$.filas").isEmpty());

        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-05-15",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "77.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "77.00", false)));

        get(s, "/contabilidad/balanza?desde=2026-05-01&hasta=2026-05-31")
                .andExpect(jsonPath("$.totalDebe").value("77.00"))
                .andExpect(jsonPath("$.totalHaber").value("77.00"));
    }

    // --------------------------------------------------------------------------------------------- diagnóstico

    /**
     * Criterio F4 (alerta de descuadre forzado): como dueño, se altera {@code saldo_cuenta_mensual} de Caja en
     * enero de 2026 sumando 50.00 de más al Debe. El Estado de Situación Financiera a una fecha de un mes posterior
     * (donde enero ya es "mes completo anterior", CLAUDE.md 10.3) responde {@code cuadra: false} con la diferencia
     * exacta, y el diagnóstico de mayorización la lista.
     */
    @Test
    void alteracionDirectaDelSaldoProduceLaAlertaConLaDiferenciaExacta() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-01-15",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "100.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "100.00", false)));

        // Alteración directa, como dueño (sin RLS): no se tocan triggers ni permisos de la aplicación
        int filas = duenio.sql("UPDATE saldo_cuenta_mensual SET total_debe = total_debe + 50.00"
                        + " WHERE empresa_id = ? AND cuenta_id = ? AND anio = 2026 AND mes = 1")
                .params(s.empresa(), cuentaId(s.empresa(), CAJA))
                .update();
        assertThat(filas).isEqualTo(1);

        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-02-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobacion.cuadra").value(false))
                .andExpect(jsonPath("$.comprobacion.diferencia").value("50.00"));

        get(s, "/contabilidad/diagnostico/mayorizacion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistente").value(false))
                .andExpect(jsonPath("$.diferencias[0].anio").value(2026))
                .andExpect(jsonPath("$.diferencias[0].mes").value(1))
                .andExpect(jsonPath("$.diferencias[0].saldoDebe").value("150.00"))
                .andExpect(jsonPath("$.diferencias[0].lineasDebe").value("100.00"));
    }

    // -------------------------------------------------------------------------------------------------- IVA

    /**
     * Criterio F4 (resumen de IVA): en febrero de 2026 el IVA débito fiscal manual es 26.00 + 2.60 = 28.60 y el de
     * la reversión es -2.60 (CLAUDE.md 12.5 aún no existe en F4: todo el IVA es manual o de su reversión), con total
     * 26.00; no hay IVA crédito fiscal en el escenario.
     */
    @Test
    void resumenDeIvaDesglosaManualYReversionEnFebrero() throws Exception {
        Sesion s = sembrarEscenario();

        get(s, "/contabilidad/reportes/iva?anio=2026&mes=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ivaDebito.manual").value("28.60"))
                .andExpect(jsonPath("$.ivaDebito.reversion").value("-2.60"))
                .andExpect(jsonPath("$.ivaDebito.n8n").value("0.00"))
                .andExpect(jsonPath("$.ivaDebito.total").value("26.00"))
                .andExpect(jsonPath("$.ivaCredito.total").value("0.00"))
                .andExpect(jsonPath("$.diferenciaEstimada").value("26.00"));
    }

    // ------------------------------------------------------------------------------------------------- errores

    /** CLAUDE.md 10.5 / ADR-038 §10: rango invertido → 422 PLT-002; cuenta de otra empresa → 404 PLT-017. */
    @Test
    void rangoInvertidoYCuentaDeOtraEmpresaSonErroresDeValidacion() throws Exception {
        Sesion a = sembrarEscenario();
        Sesion b = sesionConContabilidad();
        UUID cuentaDeB = cuentaId(b.empresa(), CAJA);

        get(a, "/contabilidad/balanza?desde=2026-12-31&hasta=2026-01-01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"));

        get(a, "/contabilidad/mayor?cuentaId=" + cuentaDeB + "&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));

        // La empresa B no sembró nada: su balanza del mismo rango está vacía (aislamiento)
        get(b, "/contabilidad/balanza?desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas").isEmpty());
    }

    /** CLAUDE.md 13: el auditor lee los cinco reportes, pero el diagnóstico exige el rol contador (403 PLT-010). */
    @Test
    void elAuditorLeeLosReportesPeroNoElDiagnostico() throws Exception {
        Sesion contador = sembrarEscenario();
        Sesion auditor = sembrarMiembro(contador, "auditor");

        get(auditor, "/contabilidad/balanza?desde=2026-01-01&hasta=2026-12-31").andExpect(status().isOk());
        get(auditor, "/contabilidad/estados/resultados?desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk());
        get(auditor, "/contabilidad/reportes/iva?anio=2026&mes=2").andExpect(status().isOk());
        get(auditor, "/contabilidad/diagnostico/mayorizacion")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }

    // ---------------------------------------------------------------------------------- bordes de fecha (corrección 1)

    /**
     * Corrección 1 — F4-03: el "mes parcial" de {@code saldoAcumuladoAFecha} (CLAUDE.md 10.3) debe incluir el propio
     * día de corte ({@code fecha <= :fecha}, no {@code <}). Un asiento fechado exactamente en {@code fechaCorte}
     * (2026-03-15, sin otros movimientos ese mes) debe entrar en el Activo y en la utilidad del ejercicio del
     * Estado de Situación Financiera a esa fecha; un día antes, el Activo y la utilidad deben seguir en cero, porque
     * el asiento todavía no existe a esa fecha. Ejercita la variante "todas las cuentas" de la consulta.
     */
    @Test
    void elEstadoDeSituacionFinancieraIncluyeElMovimientoDelPropioDiaDeCorte() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-03-15",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "100.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "100.00", false)));

        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-03-15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo.total").value("100.00"))
                .andExpect(jsonPath("$.utilidadEjercicio").value("100.00"));

        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-03-14")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo.total").value("0.00"))
                .andExpect(jsonPath("$.utilidadEjercicio").value("0.00"));
    }

    /**
     * Corrección 1 — F4-03: el Libro Mayor de una cuenta <strong>padre</strong> (dos cuentas de detalle, ADR-038
     * §5) distingue si un movimiento cae en {@code saldoInicial} o en {@code movimientos} según el mismo borde de
     * fecha. Dos asientos el 2026-04-10 (uno por cada cuenta de detalle de "Efectivo y equivalentes"): con
     * {@code desde} = ese mismo día, ambos aparecen en {@code movimientos} y el saldo inicial es cero; con
     * {@code desde} = el día siguiente, ambos pasan a {@code saldoInicial} (70.00) y {@code movimientos} queda
     * vacío. Ejercita la variante "conjunto de ids de una cuenta padre" de la consulta.
     */
    @Test
    void elMayorDeUnaCuentaPadreDistingueSaldoInicialDeMovimientosEnElDiaExacto() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID cajaGeneral = cuentaId(s.empresa(), CAJA);
        UUID cajaChica = cuentaId(s.empresa(), "11010102");
        UUID ventas = cuentaId(s.empresa(), VENTAS_GRAVADAS);
        UUID padre = cuentaId(s.empresa(), "1101");
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-04-10",
                        null,
                        LibroDiarioIT.linea(cajaGeneral, "50.00", "0", false),
                        LibroDiarioIT.linea(ventas, "0", "50.00", false)));
        registrar(
                s,
                "k2",
                LibroDiarioIT.asiento(
                        "2026-04-10",
                        null,
                        LibroDiarioIT.linea(cajaChica, "20.00", "0", false),
                        LibroDiarioIT.linea(ventas, "0", "20.00", false)));

        get(s, "/contabilidad/mayor?cuentaId=" + padre + "&desde=2026-04-10&hasta=2026-04-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial.monto").value("0.00"))
                .andExpect(jsonPath("$.movimientos.length()").value(2))
                .andExpect(jsonPath("$.saldoFinal.monto").value("70.00"));

        get(s, "/contabilidad/mayor?cuentaId=" + padre + "&desde=2026-04-11&hasta=2026-04-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial.monto").value("70.00"))
                .andExpect(jsonPath("$.movimientos.length()").value(0))
                .andExpect(jsonPath("$.saldoFinal.monto").value("70.00"));
    }

    /**
     * Corrección 1 — F4-03: la Balanza también es inclusiva en {@code hasta}: un asiento fechado el 2026-05-20
     * cuenta en Debe y en el saldo final cuando {@code hasta} es ese mismo día, y no aparece ninguna fila de la
     * cuenta cuando {@code hasta} es el día anterior (sin saldo ni movimiento, ADR-038 §6).
     */
    @Test
    void laBalanzaIncluyeElMovimientoCuandoHastaEsElMismoDiaDelAsiento() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-05-20",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "30.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "30.00", false)));

        get(s, "/contabilidad/balanza?desde=2026-05-01&hasta=2026-05-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[?(@.cuenta.codigo == '" + CAJA + "')].debe")
                        .value("30.00"))
                .andExpect(jsonPath("$.totalDebe").value("30.00"));

        get(s, "/contabilidad/balanza?desde=2026-05-01&hasta=2026-05-19")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[?(@.cuenta.codigo == '" + CAJA + "')]")
                        .doesNotExist())
                .andExpect(jsonPath("$.totalDebe").value("0.00"));
    }
}

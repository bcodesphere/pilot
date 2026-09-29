package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.contabilidad.AsientoRevertido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Pruebas de integración del Libro Diario por la API (F3-03): registro, vista previa, consulta y reversión contra
 * PostgreSQL real como {@code pilot_app}. Fuente: CLAUDE.md 10.1, 10.3 y 11.2, ADR-018, ADR-019 y ADR-036, y los
 * criterios de backend de F3 del plan de trabajo. Las fechas de los asientos son fijas y pasadas para que las
 * pruebas no dependan del día en que corran; las de «hoy» se calculan en hora de El Salvador.
 */
@RecordApplicationEvents
class LibroDiarioIT extends BaseContabilidadIT {

    /** Hoy en la zona de negocio (CON-007). */
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/El_Salvador"));

    private static final String ENERO = "2026-01-15";
    private static final String FEBRERO = "2026-02-10";
    private static final String MARZO = "2026-03-05";

    /**
     * Eventos publicados durante la prueba (Spring Test). Se usa en lugar de un oyente declarado en un
     * {@code @TestConfiguration} porque este último crearía un contexto nuevo y cada contexto cacheado retiene un pool
     * de conexiones; con demasiados el servidor de pruebas agota {@code max_connections}.
     */
    @Autowired
    private ApplicationEvents eventos;

    // ------------------------------------------------------------------------------------------- utilidades

    /** Línea de entrada en JSON; los montos van como cadena (ADR-013). */
    static String linea(UUID cuenta, String debe, String haber, boolean llevaIva) {
        return "{\"cuentaId\":\"" + cuenta + "\",\"debe\":\"" + debe + "\",\"haber\":\"" + haber + "\",\"llevaIva\":"
                + llevaIva + "}";
    }

    /** Cuerpo de un asiento manual con las líneas dadas y modo de precio opcional. */
    static String asiento(String fecha, String modo, String... lineas) {
        return "{\"fecha\":\"" + fecha + "\",\"concepto\":\"Asiento de prueba\""
                + (modo == null ? "" : ",\"modoPrecio\":\"" + modo + "\"") + ",\"lineas\":["
                + String.join(",", lineas) + "]}";
    }

    /** Asiento simple Caja / Ventas por el monto, sin IVA. */
    private String simple(Sesion s, String fecha, String monto) {
        return asiento(
                fecha,
                null,
                linea(cuentaId(s.empresa(), "11010101"), monto, "0", false),
                linea(cuentaId(s.empresa(), "51010101"), "0", monto, false));
    }

    /** Registra con una clave nueva y devuelve el id del asiento creado. */
    private String registrar(Sesion s, String cuerpo) throws Exception {
        return leer(
                postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                        .andExpect(status().isCreated()),
                "$.id");
    }

    private int asientos(Sesion s) {
        return contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa());
    }

    /** Saldo (Debe − Haber) acumulado de una cuenta en {@code saldo_cuenta_mensual}. */
    private BigDecimal saldo(Sesion s, String codigo) {
        return duenio.sql("SELECT COALESCE(SUM(total_debe - total_haber), 0) FROM saldo_cuenta_mensual"
                        + " WHERE empresa_id = ? AND cuenta_id = ?")
                .params(s.empresa(), cuentaId(s.empresa(), codigo))
                .query(BigDecimal.class)
                .single();
    }

    /** Código de la cuenta de IVA débito o crédito de la configuración de la empresa. */
    private String codigoIva(Sesion s, String cual) throws Exception {
        return leer(get(s, "/contabilidad/configuracion"), "$." + cual + ".codigo");
    }

    /** Verifica que no quedó rastro del asiento rechazado: ni cabecera, ni líneas, ni saldos, ni correlativo. */
    private void nadaGuardado(Sesion s) {
        assertThat(asientos(s)).isZero();
        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM correlativo_asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }

    // ---------------------------------------------------------------------------------------------- registro

    /** Criterio F3: registrar numera 1 y luego 2, guarda las líneas y acumula los saldos del mes (CLAUDE.md 10.3). */
    @Test
    void registrarNumeraGuardaLasLineasYMayoriza() throws Exception {
        Sesion s = sesionConContabilidad();

        postConClave(s, "/contabilidad/asientos", "k1", simple(s, ENERO, "100.00"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.anio").value(2026))
                .andExpect(jsonPath("$.estado").value("CONTABILIZADO"))
                .andExpect(jsonPath("$.origenTipo").value("MANUAL"))
                .andExpect(jsonPath("$.modoPrecio").doesNotExist())
                .andExpect(jsonPath("$.totalDebe").value("100.00"))
                .andExpect(jsonPath("$.totalHaber").value("100.00"))
                .andExpect(jsonPath("$.lineas.length()").value(2))
                .andExpect(jsonPath("$.lineas[0].numeroLinea").value(1))
                .andExpect(jsonPath("$.lineas[0].cuenta.codigo").value("11010101"))
                .andExpect(jsonPath("$.lineas[0].debe").value("100.00"))
                .andExpect(jsonPath("$.lineas[1].haber").value("100.00"));
        postConClave(s, "/contabilidad/asientos", "k2", simple(s, ENERO, "50.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(2));

        // Un año distinto reinicia la numeración (correlativo por empresa y año)
        postConClave(s, "/contabilidad/asientos", "k3", simple(s, "2025-12-20", "10.00"))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.anio").value(2025));

        // Líneas y saldos quedan guardados: enero de 2026 acumula 150.00 en Caja (Debe) y en Ventas (Haber)
        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(6);
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("160.00");
        assertThat(saldo(s, "51010101")).isEqualByComparingTo("-160.00");
        assertThat(contar(
                        "SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ? AND anio = 2026 AND mes = 1",
                        s.empresa()))
                .isEqualTo(2);
    }

    /** Criterio F3 (IVA), ejemplo CON_IVA de 11.2: Caja 113.00 / Ventas 113.00 con IVA → Ventas 100.00 + IVA débito 13.00. */
    @Test
    void registrarConIvaEnModoConIvaGuardaLasLineasDeLaSeccion112() throws Exception {
        Sesion s = sesionConContabilidad();
        String ivaDebito = codigoIva(s, "cuentaIvaDebito");

        postConClave(
                        s,
                        "/contabilidad/asientos",
                        "k1",
                        asiento(
                                ENERO,
                                "CON_IVA",
                                linea(cuentaId(s.empresa(), "11010101"), "113.00", "0", false),
                                linea(cuentaId(s.empresa(), "51010101"), "0", "113.00", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modoPrecio").value("CON_IVA"))
                .andExpect(jsonPath("$.totalDebe").value("113.00"))
                .andExpect(jsonPath("$.lineas.length()").value(3))
                .andExpect(jsonPath("$.lineas[1].cuenta.codigo").value("51010101"))
                .andExpect(jsonPath("$.lineas[1].haber").value("100.00"))
                .andExpect(jsonPath("$.lineas[1].origenLinea").value("USUARIO"))
                .andExpect(jsonPath("$.lineas[2].cuenta.codigo").value(ivaDebito))
                .andExpect(jsonPath("$.lineas[2].haber").value("13.00"))
                .andExpect(jsonPath("$.lineas[2].origenLinea").value("IVA_CALCULADO"));

        // La línea de IVA queda enlazada a su base y el IVA se mayoriza en su cuenta
        assertThat(contar(
                        "SELECT count(*) FROM asiento_linea i JOIN asiento_linea b ON b.id = i.linea_base_id"
                                + " WHERE i.empresa_id = ? AND i.origen_linea = 'IVA_CALCULADO' AND b.numero_linea = 2",
                        s.empresa()))
                .isEqualTo(1);
        assertThat(saldo(s, ivaDebito)).isEqualByComparingTo("-13.00");
    }

    /** Criterio F3 (IVA), ejemplo SIN_IVA de 11.2: Compras 100.00 con IVA / Caja 113.00 → Compras 100.00 + IVA crédito 13.00. */
    @Test
    void registrarConIvaEnModoSinIvaGuardaLasLineasDeLaSeccion112() throws Exception {
        Sesion s = sesionConContabilidad();
        String ivaCredito = codigoIva(s, "cuentaIvaCredito");
        String compras = codigoCuentaDeCostos(s);

        postConClave(
                        s,
                        "/contabilidad/asientos",
                        "k1",
                        asiento(
                                ENERO,
                                "SIN_IVA",
                                linea(cuentaId(s.empresa(), compras), "100.00", "0", true),
                                linea(cuentaId(s.empresa(), "11010101"), "0", "113.00", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modoPrecio").value("SIN_IVA"))
                .andExpect(jsonPath("$.totalDebe").value("113.00"))
                .andExpect(jsonPath("$.totalHaber").value("113.00"))
                .andExpect(jsonPath("$.lineas.length()").value(3))
                .andExpect(jsonPath("$.lineas[0].debe").value("100.00"))
                .andExpect(jsonPath("$.lineas[1].cuenta.codigo").value(ivaCredito))
                .andExpect(jsonPath("$.lineas[1].debe").value("13.00"))
                .andExpect(jsonPath("$.lineas[2].haber").value("113.00"));
    }

    /** Sin modo en el cuerpo se usa el de la configuración de la empresa (CON_IVA por defecto, V11). */
    @Test
    void sinModoEnElCuerpoSeUsaElDeLaConfiguracion() throws Exception {
        Sesion s = sesionConContabilidad();

        postConClave(
                        s,
                        "/contabilidad/asientos",
                        "k1",
                        asiento(
                                ENERO,
                                null,
                                linea(cuentaId(s.empresa(), "11010101"), "113.00", "0", false),
                                linea(cuentaId(s.empresa(), "51010101"), "0", "113.00", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modoPrecio").value("CON_IVA"))
                .andExpect(jsonPath("$.lineas[1].haber").value("100.00"));
    }

    /** Primera cuenta de detalle de la clase 4 (costos y gastos) del catálogo base, para probar compras. */
    /**
     * Cuenta de costos que ni la configuración ni ninguna regla activa usan (catálogo ADR-044, tarea CAT: varias
     * reglas guiadas ahora apuntan a cuentas de clase 4 por defecto, así que ya no basta con "la primera de clase
     * 4"; se excluyen explícitamente las que están en uso).
     */
    private String codigoCuentaDeCostos(Sesion s) {
        return duenio.sql("SELECT c.codigo FROM cuenta_contable c WHERE c.empresa_id = ? AND c.acepta_movimientos"
                        + " AND c.codigo LIKE '4%'"
                        + " AND NOT EXISTS (SELECT 1 FROM regla_contabilizacion r"
                        + "                 WHERE r.empresa_id = c.empresa_id AND r.cuenta_id = c.id AND r.activa)"
                        + " AND NOT EXISTS (SELECT 1 FROM configuracion_contable f WHERE f.empresa_id = c.empresa_id"
                        + "                 AND c.id IN (f.cuenta_iva_debito_id, f.cuenta_iva_credito_id))"
                        + " ORDER BY c.codigo LIMIT 1")
                .param(s.empresa())
                .query(String.class)
                .single();
    }

    // -------------------------------------------------------------------------------------------- rechazos

    /**
     * Criterio F3: cada rechazo de forma y de partida doble responde 422 con su código y no guarda nada (ni asiento, ni
     * líneas, ni saldos, ni correlativo). {@code CON-004} no es alcanzable por la API: una línea válida ya tiene un monto
     * mayor que cero, así que el total en cero solo se prueba en el dominio ({@code ReglasAsientoTest}).
     */
    @Test
    void losRechazosDeFormaYPartidaDobleDan422YNoGuardanNada() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        // CON-001: una sola línea, y también ninguna
        rechazo(s, asiento(ENERO, null, linea(caja, "10.00", "0", false)), "CON-001");
        rechazo(s, asiento(ENERO, null), "CON-001");
        // CON-002: Debe y Haber a la vez, o ninguno de los dos
        rechazo(
                s,
                asiento(ENERO, null, linea(caja, "10.00", "10.00", false), linea(ventas, "0", "10.00", false)),
                "CON-002");
        rechazo(s, asiento(ENERO, null, linea(caja, "0", "0", false), linea(ventas, "0", "10.00", false)), "CON-002");
        // CON-003: monto negativo y monto con más de 2 decimales
        rechazo(
                s,
                asiento(ENERO, null, linea(caja, "-10.00", "0", false), linea(ventas, "0", "10.00", false)),
                "CON-003");
        rechazo(
                s,
                asiento(ENERO, null, linea(caja, "10.005", "0", false), linea(ventas, "0", "10.00", false)),
                "CON-003");
        // CON-005: descuadre, con la diferencia exacta en el Problem Details
        postConClave(
                        s,
                        "/contabilidad/asientos",
                        "k-desc",
                        asiento(ENERO, null, linea(caja, "100.00", "0", false), linea(ventas, "0", "99.50", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-005"))
                .andExpect(jsonPath("$.diferencia").value("0.50"));

        nadaGuardado(s);
    }

    /** Criterio F3: los rechazos de cuenta, fecha e IVA responden 422 con su código y no guardan nada. */
    @Test
    void losRechazosDeCuentaFechaEIvaDan422YNoGuardanNada() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        UUID ivaDebito = cuentaId(s.empresa(), codigoIva(s, "cuentaIvaDebito"));
        UUID padre = cuentaId(s.empresa(), "110101");
        // CON-006: una cuenta padre, una inexistente y una de otra empresa; el error apunta a lineas[i].cuentaId
        Sesion otra = sesionConContabilidad();
        UUID ajena = cuentaId(otra.empresa(), "11010101");
        for (UUID mala : List.of(padre, UUID.randomUUID(), ajena)) {
            postConClave(
                            s,
                            "/contabilidad/asientos",
                            "k-" + mala,
                            asiento(ENERO, null, linea(mala, "10.00", "0", false), linea(ventas, "0", "10.00", false)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CON-006"))
                    .andExpect(jsonPath("$.errores[0].campo").value("lineas[0].cuentaId"));
        }
        // CON-007: fecha futura
        rechazo(s, simple(s, HOY.plusDays(2).toString(), "10.00"), "CON-007");
        // CON-013: «lleva IVA» sobre una cuenta de IVA
        rechazo(
                s,
                asiento(ENERO, null, linea(caja, "10.00", "0", false), linea(ivaDebito, "0", "10.00", true)),
                "CON-013");
        // CON-017: sin tasa vigente a la fecha (la única vigencia arranca el 2000-01-01, ADR-034)
        rechazo(
                s,
                asiento("1999-12-31", null, linea(caja, "113.00", "0", false), linea(ventas, "0", "113.00", true)),
                "CON-017");

        nadaGuardado(s);
    }

    private void rechazo(Sesion s, String cuerpo, String codigo) throws Exception {
        postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(codigo));
    }

    // ------------------------------------------------------------------------------------------ idempotencia

    /** Criterio F3 (idempotencia): misma clave y mismo cuerpo → misma respuesta con Idempotency-Replayed y un solo asiento. */
    @Test
    void lamismaClaveYElMismoCuerpoDevuelvenLaRespuestaOriginal() throws Exception {
        Sesion s = sesionConContabilidad();
        String cuerpo = simple(s, ENERO, "25.00");

        String primera = postConClave(s, "/contabilidad/asientos", "clave-1", cuerpo)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String segunda = postConClave(s, "/contabilidad/asientos", "clave-1", cuerpo)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(segunda).isEqualTo(primera);
        assertThat(asientos(s)).isEqualTo(1);
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("25.00");
    }

    /** Idempotencia: la misma clave con otro cuerpo → 422 PLT-005; sin la clave → 428 PLT-006. */
    @Test
    void laMismaClaveConOtroCuerpoEs422YSinClaveEs428() throws Exception {
        Sesion s = sesionConContabilidad();
        postConClave(s, "/contabilidad/asientos", "clave-1", simple(s, ENERO, "25.00"))
                .andExpect(status().isCreated());

        postConClave(s, "/contabilidad/asientos", "clave-1", simple(s, ENERO, "26.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-005"));
        postConClave(s, "/contabilidad/asientos", null, simple(s, ENERO, "25.00"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.codigo").value("PLT-006"));

        assertThat(asientos(s)).isEqualTo(1);
    }

    /** Un rechazo de negocio no consume la clave: corregido el cuerpo, la misma clave sirve (los 4xx no se guardan). */
    @Test
    void unRechazoNoConsumeLaClave() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        postConClave(
                        s,
                        "/contabilidad/asientos",
                        "clave-1",
                        asiento(ENERO, null, linea(caja, "10.00", "0", false), linea(ventas, "0", "9.00", false)))
                .andExpect(status().isUnprocessableEntity());
        postConClave(s, "/contabilidad/asientos", "clave-1", simple(s, ENERO, "10.00"))
                .andExpect(status().isCreated());

        assertThat(asientos(s)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------- vista previa

    /** Criterio F3 (ADR-036): la vista previa de un asiento descuadrado responde 200 con cuadra=false y la diferencia, sin escribir nada. */
    @Test
    void laVistaPreviaDeUnDescuadreEs200SinEscribirNada() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        post(
                        s,
                        "/contabilidad/asientos/vista-previa",
                        asiento(ENERO, null, linea(caja, "100.00", "0", false), linea(ventas, "0", "60.00", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadra").value(false))
                .andExpect(jsonPath("$.diferencia").value("40.00"))
                .andExpect(jsonPath("$.totalDebe").value("100.00"))
                .andExpect(jsonPath("$.totalHaber").value("60.00"))
                .andExpect(jsonPath("$.modoPrecio").doesNotExist())
                .andExpect(jsonPath("$.tasaIva").doesNotExist())
                .andExpect(jsonPath("$.lineas.length()").value(2));
        // Incluso una sola línea se previsualiza (CON-001 no impide expandir)
        post(s, "/contabilidad/asientos/vista-previa", asiento(ENERO, null, linea(caja, "100.00", "0", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadra").value(false));

        nadaGuardado(s);
    }

    /** Vista previa de los dos ejemplos de 11.2 con las líneas expandidas, el modo aplicado y la tasa vigente. */
    @Test
    void laVistaPreviaExpandeLosEjemplosDeLaSeccion112() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        UUID compras = cuentaId(s.empresa(), codigoCuentaDeCostos(s));

        post(
                        s,
                        "/contabilidad/asientos/vista-previa",
                        asiento(
                                ENERO,
                                "CON_IVA",
                                linea(caja, "113.00", "0", false),
                                linea(ventas, "0", "113.00", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadra").value(true))
                .andExpect(jsonPath("$.modoPrecio").value("CON_IVA"))
                .andExpect(jsonPath("$.tasaIva").value("0.1300"))
                .andExpect(jsonPath("$.lineas.length()").value(3))
                .andExpect(jsonPath("$.lineas[1].haber").value("100.00"))
                .andExpect(jsonPath("$.lineas[1].numeroLineaOrigen").doesNotExist())
                .andExpect(jsonPath("$.lineas[2].haber").value("13.00"))
                .andExpect(jsonPath("$.lineas[2].origenLinea").value("IVA_CALCULADO"))
                .andExpect(jsonPath("$.lineas[2].numeroLineaOrigen").value(2));
        post(
                        s,
                        "/contabilidad/asientos/vista-previa",
                        asiento(
                                ENERO,
                                "SIN_IVA",
                                linea(compras, "100.00", "0", true),
                                linea(caja, "0", "113.00", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadra").value(true))
                .andExpect(jsonPath("$.totalDebe").value("113.00"))
                .andExpect(jsonPath("$.lineas[1].debe").value("13.00"))
                .andExpect(jsonPath("$.lineas[1].numeroLineaOrigen").value(1));
        // Sin expandir (CON-003 o cuenta inválida) sí responde 422
        post(
                        s,
                        "/contabilidad/asientos/vista-previa",
                        asiento(ENERO, null, linea(caja, "-1", "0", false), linea(ventas, "0", "1.00", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-003"));

        nadaGuardado(s);
    }

    // ------------------------------------------------------------------------------------------------ reversión

    /** Criterio F3: la reversión intercambia lados, marca el original REVERTIDO, restaura los saldos y publica AsientoRevertido. */
    @Test
    void revertirIntercambiaLadosRestauraLosSaldosYPublicaElEvento() throws Exception {
        Sesion s = sesionConContabilidad();
        String original = registrar(s, simple(s, ENERO, "100.00"));
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("100.00");

        String reversion = leer(
                postConClave(
                                s,
                                "/contabilidad/asientos/" + original + "/reversion",
                                "r1",
                                "{\"fecha\":\"" + FEBRERO + "\"}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.origenTipo").value("REVERSION"))
                        .andExpect(jsonPath("$.estado").value("CONTABILIZADO"))
                        .andExpect(jsonPath("$.numero").value(2))
                        .andExpect(jsonPath("$.fecha").value(FEBRERO))
                        .andExpect(jsonPath("$.concepto").value("Reversión del asiento N.º 1/2026"))
                        .andExpect(jsonPath("$.asientoRevertidoId").value(original))
                        .andExpect(jsonPath("$.lineas[0].cuenta.codigo").value("11010101"))
                        .andExpect(jsonPath("$.lineas[0].debe").value("0.00"))
                        .andExpect(jsonPath("$.lineas[0].haber").value("100.00"))
                        .andExpect(jsonPath("$.lineas[1].debe").value("100.00")),
                "$.id");

        // El original queda REVERTIDO con el enlace a su reversión; la reversión mayoriza y los saldos vuelven a cero
        get(s, "/contabilidad/asientos/" + original)
                .andExpect(jsonPath("$.estado").value("REVERTIDO"))
                .andExpect(jsonPath("$.asientoReversionId").value(reversion));
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("0.00");
        assertThat(saldo(s, "51010101")).isEqualByComparingTo("0.00");
        // Cada mes conserva su propio acumulado (enero: 100 Debe; febrero: 100 Haber)
        assertThat(contar("SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ? AND mes = 2", s.empresa()))
                .isEqualTo(2);

        // El evento síncrono se publicó con los datos del original
        assertThat(eventos.stream(AsientoRevertido.class)).anySatisfy(e -> {
            assertThat(e.asientoOriginalId()).isEqualTo(UUID.fromString(original));
            assertThat(e.asientoReversionId()).isEqualTo(UUID.fromString(reversion));
            assertThat(e.empresaId().valor()).isEqualTo(s.empresa());
            assertThat(e.origenTipo()).isEqualTo("MANUAL");
            assertThat(e.origenId()).isNull();
        });
        // La auditoría registró REVERTIR en el original y CREAR en la reversión
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'asiento' AND accion = 'REVERTIR'"
                                + " AND entidad_id = ?",
                        s.empresa(),
                        original))
                .isEqualTo(1);
    }

    /** La reversión sin cuerpo usa hoy en hora de El Salvador; la reversión con IVA enlaza sus líneas. */
    @Test
    void revertirSinCuerpoUsaHoyYConservaLosEnlacesDeIva() throws Exception {
        Sesion s = sesionConContabilidad();
        String original = registrar(
                s,
                asiento(
                        ENERO,
                        "CON_IVA",
                        linea(cuentaId(s.empresa(), "11010101"), "113.00", "0", false),
                        linea(cuentaId(s.empresa(), "51010101"), "0", "113.00", true)));

        postConClave(s, "/contabilidad/asientos/" + original + "/reversion", "r1", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fecha").value(HOY.toString()))
                .andExpect(jsonPath("$.lineas.length()").value(3))
                .andExpect(jsonPath("$.lineas[2].origenLinea").value("IVA_CALCULADO"))
                .andExpect(jsonPath("$.lineas[2].debe").value("13.00"));

        assertThat(contar(
                        "SELECT count(*) FROM asiento_linea i JOIN asiento_linea b ON b.id = i.linea_base_id"
                                + " WHERE i.empresa_id = ? AND i.origen_linea = 'IVA_CALCULADO'",
                        s.empresa()))
                .isEqualTo(2);
    }

    /** CON-008 al revertir dos veces, CON-009 al revertir una reversión, CON-007 y CON-018 por la fecha; nada más se guarda. */
    @Test
    void losRechazosDeLaReversionNoGuardanNada() throws Exception {
        Sesion s = sesionConContabilidad();
        String original = registrar(s, simple(s, MARZO, "100.00"));
        String ruta = "/contabilidad/asientos/" + original + "/reversion";

        // Fecha futura y fecha anterior a la del original: se rechazan y el asiento sigue vigente
        postConClave(s, ruta, "f1", "{\"fecha\":\"" + HOY.plusDays(2) + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-007"));
        postConClave(s, ruta, "f2", "{\"fecha\":\"" + FEBRERO + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-018"));
        assertThat(asientos(s)).isEqualTo(1);
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("100.00");

        // La reversión válida procede; una segunda es CON-008 y revertir la reversión es CON-009
        String reversion = leer(
                postConClave(s, ruta, "ok", "{\"fecha\":\"" + MARZO + "\"}").andExpect(status().isCreated()), "$.id");
        postConClave(s, ruta, "otra", "{\"fecha\":\"" + MARZO + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CON-008"));
        postConClave(s, "/contabilidad/asientos/" + reversion + "/reversion", "r2", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CON-009"));

        assertThat(asientos(s)).isEqualTo(2);
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("0.00");
    }

    /** Idempotencia de la reversión: repetir la clave devuelve la misma respuesta y no revierte dos veces. */
    @Test
    void repetirLaReversionConLaMismaClaveEsIdempotente() throws Exception {
        Sesion s = sesionConContabilidad();
        String original = registrar(s, simple(s, MARZO, "100.00"));
        String ruta = "/contabilidad/asientos/" + original + "/reversion";

        String primera = postConClave(s, ruta, "r1", "{\"fecha\":\"" + MARZO + "\"}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String segunda = postConClave(s, ruta, "r1", "{\"fecha\":\"" + MARZO + "\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(segunda).isEqualTo(primera);
        assertThat(asientos(s)).isEqualTo(2);
        postConClave(s, ruta, null, "").andExpect(status().isPreconditionRequired());
    }

    // ------------------------------------------------------------------------------------------- concurrencia

    /** Criterio F3: 50 asientos simultáneos de la misma empresa → números 1 a 50 sin huecos ni duplicados y saldos exactos. */
    @Test
    void cincuentaAsientosSimultaneosNumeranSinHuecosNiDuplicados() throws Exception {
        Sesion s = sesionConContabilidad();
        String cuerpo = simple(s, ENERO, "10.00");
        int total = 50;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(total);
        java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> resultados = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < total; i++) {
                String clave = "concurrente-" + i;
                resultados.add(pool.submit(() -> {
                    // Todos los hilos arrancan a la vez para maximizar la competencia por el correlativo
                    salida.await();
                    return postConClave(s, "/contabilidad/asientos", clave, cuerpo)
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            salida.countDown();
            for (java.util.concurrent.Future<Integer> r : resultados) {
                assertThat(r.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Long> numeros = duenio.sql("SELECT numero FROM asiento WHERE empresa_id = ? ORDER BY numero")
                .param(s.empresa())
                .query(Long.class)
                .list();
        assertThat(numeros)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, total)
                        .boxed()
                        .toList());
        assertThat(saldo(s, "11010101")).isEqualByComparingTo("500.00");
        assertThat(saldo(s, "51010101")).isEqualByComparingTo("-500.00");
    }

    // ------------------------------------------------------------------------------- CON-011 y CON-012 reales

    /** ADR-035 (decisión 5): con movimientos reales, cambiar el código es CON-011 y desactivar con saldo es CON-012; con saldo 0 se permite. */
    @Test
    void elCatalogoRespetaLosMovimientosYElSaldoReales() throws Exception {
        Sesion s = sesionConContabilidad();
        String compras = codigoCuentaDeCostos(s);
        UUID cuenta = cuentaId(s.empresa(), compras);
        String nuevoCodigo = compras.substring(0, 6) + "98";
        // Compras al Debe contra Caja; la cuenta de costos no la usan la configuración ni las reglas (sí podría
        // desactivarse)
        String original = registrar(
                s,
                asiento(
                        ENERO,
                        null,
                        linea(cuenta, "40.00", "0", false),
                        linea(cuentaId(s.empresa(), "11010101"), "0", "40.00", false)));

        patch(s, "/contabilidad/cuentas/" + cuenta, "\"0\"", "{\"codigo\":\"" + nuevoCodigo + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-011"));
        patch(s, "/contabilidad/cuentas/" + cuenta, "\"0\"", "{\"activa\":false}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-012"));

        // Revertido el asiento el saldo vuelve a 0 y desactivar sí se permite (la cuenta sigue teniendo movimientos)
        postConClave(s, "/contabilidad/asientos/" + original + "/reversion", "r1", "{\"fecha\":\"" + ENERO + "\"}")
                .andExpect(status().isCreated());
        patch(s, "/contabilidad/cuentas/" + cuenta, "\"0\"", "{\"activa\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        // Y una cuenta inactiva ya no acepta asientos (CON-006)
        postConClave(
                        s,
                        "/contabilidad/asientos",
                        "k-inactiva",
                        asiento(
                                ENERO,
                                null,
                                linea(cuenta, "1.00", "0", false),
                                linea(cuentaId(s.empresa(), "11010101"), "0", "1.00", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"));
    }

    // ---------------------------------------------------------------------------------------------- consulta

    /** Consulta de un asiento con líneas y resumen de cuenta; 404 PLT-017 si no existe o es de otra empresa. */
    @Test
    void obtenerDevuelveElAsientoYOtraEmpresaNoLoVeNiLoRevierte() throws Exception {
        Sesion s = sesionConContabilidad();
        Sesion otra = sesionConContabilidad();
        String id = registrar(s, simple(s, ENERO, "100.00"));

        get(s, "/contabilidad/asientos/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.lineas[0].cuenta.nombre").value("Caja General"))
                .andExpect(jsonPath("$.creadoEn").exists());
        get(otra, "/contabilidad/asientos/" + id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        postConClave(otra, "/contabilidad/asientos/" + id + "/reversion", "x", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        get(s, "/contabilidad/asientos/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));

        // La otra empresa no ve nada en su Libro Diario
        get(otra, "/contabilidad/asientos")
                .andExpect(jsonPath("$.elementos.length()").value(0));
        assertThat(asientos(otra)).isZero();
    }

    /**
     * Siembra el Libro Diario de las pruebas de listado: 2025-1, 2026-1 (enero), 2026-2 (febrero), 2026-3 (marzo, con la
     * cuenta de costos) y 2026-4 (reversión de enero, revertido el original).
     *
     * @return el id del asiento de enero, que quedó REVERTIDO
     */
    private String sembrarLibro(Sesion s, UUID compras) throws Exception {
        registrar(s, simple(s, "2025-12-20", "5.00"));
        String enero = registrar(s, simple(s, ENERO, "10.00"));
        registrar(s, simple(s, FEBRERO, "20.00"));
        registrar(
                s,
                asiento(
                        MARZO,
                        null,
                        linea(compras, "30.00", "0", false),
                        linea(cuentaId(s.empresa(), "11010101"), "0", "30.00", false)));
        // La reversión de enero es el asiento 2026-4 con origen REVERSION
        postConClave(s, "/contabilidad/asientos/" + enero + "/reversion", "r", "{\"fecha\":\"" + MARZO + "\"}")
                .andExpect(status().isCreated());
        return enero;
    }

    /** Listado: orden por (año, número), cursor opaco y límite, sin repetir ni saltar asientos. */
    @Test
    void elLibroDiarioSePaginaPorCursor() throws Exception {
        Sesion s = sesionConContabilidad();
        sembrarLibro(s, cuentaId(s.empresa(), codigoCuentaDeCostos(s)));

        // Orden ascendente por año y número
        get(s, "/contabilidad/asientos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(5))
                .andExpect(jsonPath("$.elementos[0].anio").value(2025))
                .andExpect(jsonPath("$.elementos[1].numero").value(1))
                .andExpect(jsonPath("$.elementos[4].numero").value(4))
                .andExpect(jsonPath("$.siguienteCursor").doesNotExist());

        // Paginación por cursor con límite 2: 2 + 2 + 1, sin repetir ni saltar asientos
        String pagina1 = get(s, "/contabilidad/asientos?limite=2")
                .andExpect(jsonPath("$.elementos.length()").value(2))
                .andExpect(jsonPath("$.siguienteCursor").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor1 = com.jayway.jsonpath.JsonPath.read(pagina1, "$.siguienteCursor");
        String pagina2 = get(s, "/contabilidad/asientos?limite=2&cursor=" + cursor1)
                .andExpect(jsonPath("$.elementos.length()").value(2))
                .andExpect(jsonPath("$.elementos[0].anio").value(2026))
                .andExpect(jsonPath("$.elementos[0].numero").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor2 = com.jayway.jsonpath.JsonPath.read(pagina2, "$.siguienteCursor");
        get(s, "/contabilidad/asientos?limite=2&cursor=" + cursor2)
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].numero").value(4))
                .andExpect(jsonPath("$.siguienteCursor").doesNotExist());

        // Un cursor ilegible es 422 PLT-002
        get(s, "/contabilidad/asientos?cursor=no-es-un-cursor")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"));
    }

    /** Listado: fechas (inclusivas), año, número, origen, estado y cuenta; un rango invertido es 422 PLT-002. */
    @Test
    void elLibroDiarioSeFiltra() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID compras = cuentaId(s.empresa(), codigoCuentaDeCostos(s));
        String enero = sembrarLibro(s, compras);

        get(s, "/contabilidad/asientos?desde=" + FEBRERO + "&hasta=" + MARZO)
                .andExpect(jsonPath("$.elementos.length()").value(3));
        get(s, "/contabilidad/asientos?anio=2025")
                .andExpect(jsonPath("$.elementos.length()").value(1));
        get(s, "/contabilidad/asientos?anio=2026&numero=3")
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].fecha").value(MARZO));
        get(s, "/contabilidad/asientos?origen=REVERSION")
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].origenTipo").value("REVERSION"));
        get(s, "/contabilidad/asientos?estado=REVERTIDO")
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].id").value(enero));
        get(s, "/contabilidad/asientos?cuentaId=" + compras)
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].numero").value(3));
        get(s, "/contabilidad/asientos?cuentaId=" + cuentaId(s.empresa(), "11010101"))
                .andExpect(jsonPath("$.elementos.length()").value(5));

        // Rango invertido: 422 PLT-002
        get(s, "/contabilidad/asientos?desde=" + MARZO + "&hasta=" + ENERO)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"));
    }

    // -------------------------------------------------------------------------------------------------- roles

    /** Roles de 13: el auditor lee el Libro Diario pero no registra, previsualiza ni revierte (403 PLT-010). */
    @Test
    void elAuditorLeeYNoEscribe() throws Exception {
        Sesion contador = sesionConContabilidad();
        String id = registrar(contador, simple(contador, ENERO, "10.00"));
        Sesion auditor = sembrarMiembro(contador, "auditor");

        get(auditor, "/contabilidad/asientos").andExpect(status().isOk());
        get(auditor, "/contabilidad/asientos/" + id).andExpect(status().isOk());
        postConClave(auditor, "/contabilidad/asientos", "k", simple(contador, ENERO, "10.00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        post(auditor, "/contabilidad/asientos/vista-previa", simple(contador, ENERO, "10.00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        postConClave(auditor, "/contabilidad/asientos/" + id + "/reversion", "r", "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        assertThat(asientos(contador)).isEqualTo(1);
    }
}

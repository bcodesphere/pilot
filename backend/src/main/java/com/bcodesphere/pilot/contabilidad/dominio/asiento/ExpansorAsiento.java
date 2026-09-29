package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ReglasCatalogo;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import com.bcodesphere.pilot.contabilidad.dominio.iva.CalculadoraIva;
import com.bcodesphere.pilot.contabilidad.dominio.iva.ConsultaTasaIva;
import com.bcodesphere.pilot.contabilidad.dominio.iva.SeparacionIva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expande las líneas capturadas de un asiento manual (CLAUDE.md 11.2): valida lo que impide expandir (fecha, montos,
 * un solo lado, cuentas, IVA sobre cuentas de IVA, tasa vigente) y separa la base y el IVA de las líneas que "llevan
 * IVA". Es la parte común de la vista previa y del registro; la partida doble se valida aparte
 * ({@link ReglasAsiento#validar}) porque la vista previa no la rechaza (ADR-036).
 */
public final class ExpansorAsiento {

    private ExpansorAsiento() {}

    /** Línea capturada con sus montos ya leídos y validados. */
    private record LineaLeida(int posicion, LineaSolicitud origen, Dinero debe, Dinero haber, Cuenta cuenta) {

        /** Monto de la línea, que va en el único lado distinto de cero. */
        Dinero monto() {
            return debe.esCero() ? haber : debe;
        }
    }

    /**
     * Expande un asiento manual.
     *
     * @param solicitud datos capturados
     * @param hoy fecha de hoy en {@code America/El_Salvador}
     * @param cuentas cuentas de la empresa por id, ya consultadas; una cuenta que falte se trata como inexistente
     * @param configuracion configuración contable (modo de precio por defecto y cuentas de IVA)
     * @param tasas puerto de la tasa de IVA vigente
     * @return el asiento expandido, sin validar la partida doble
     * @throws ExcepcionContabilidad {@code CON-002}, {@code CON-003}, {@code CON-007}, {@code CON-013} o {@code CON-017}
     * @throws ExcepcionValidacion {@code CON-006} con {@code errores[].campo = lineas[i].cuentaId}
     */
    public static AsientoExpandido expandir(
            SolicitudAsiento solicitud,
            LocalDate hoy,
            Map<UUID, Cuenta> cuentas,
            ConfiguracionContable configuracion,
            ConsultaTasaIva tasas) {
        // 1. CON-007: la fecha es obligatoria y no futura
        ReglasAsiento.validarFecha(solicitud.fecha(), hoy);

        // 2. CON-003 y CON-002: lee cada monto y comprueba que la línea lleve un solo lado mayor que cero
        List<LineaLeida> leidas = leerLineas(solicitud, cuentas);

        // 3. CON-006: todas las cuentas deben existir en la empresa, estar activas y ser de detalle (se informan
        // juntas)
        exigirCuentasImputables(leidas);

        // 4. CON-013: "lleva IVA" no se permite sobre las cuentas de IVA de la configuración
        leidas.stream()
                .filter(l -> l.origen().llevaIva() && esCuentaDeIva(l.cuenta(), configuracion))
                .findFirst()
                .ifPresent(l -> {
                    throw ExcepcionContabilidad.ivaSobreCuentaDeIva(l.posicion());
                });

        // 5. CON-017: la tasa se lee con vigencia a la fecha del asiento y solo si alguna línea la necesita
        boolean conIva = leidas.stream().anyMatch(l -> l.origen().llevaIva());
        BigDecimal tasa =
                conIva ? tasas.vigenteA(solicitud.fecha()).orElseThrow(ExcepcionContabilidad::sinTasaVigente) : null;
        ModoPrecio modo = solicitud.modoPrecio() != null ? solicitud.modoPrecio() : configuracion.modoPrecioDefecto();

        // 6. Expande cada línea; el modo y la tasa se informan solo si alguna línea llevó IVA (ADR-036)
        List<LineaExpandida> expandidas = new ArrayList<>();
        for (LineaLeida l : leidas) {
            expandirLinea(l, modo, tasa, configuracion, expandidas);
        }
        return new AsientoExpandido(
                solicitud.fecha(),
                solicitud.concepto(),
                conIva ? modo : null,
                tasa,
                expandidas,
                solicitud.lineas().size());
    }

    /** Lee los montos y aplica CON-003 (formato) y CON-002 (un solo lado) línea por línea. */
    private static List<LineaLeida> leerLineas(SolicitudAsiento solicitud, Map<UUID, Cuenta> cuentas) {
        List<LineaLeida> leidas = new ArrayList<>();
        int posicion = 0;
        for (LineaSolicitud l : solicitud.lineas()) {
            posicion++;
            Dinero debe = LectorMontos.leer(l.debe(), posicion, "Debe");
            Dinero haber = LectorMontos.leer(l.haber(), posicion, "Haber");
            // Exactamente uno de los dos lados es mayor que cero: ambos o ninguno es CON-002
            if (debe.esCero() == haber.esCero()) {
                throw ExcepcionContabilidad.lineaConAmbosLados(posicion);
            }
            leidas.add(new LineaLeida(posicion, l, debe, haber, cuentas.get(l.cuentaId())));
        }
        return leidas;
    }

    /** Lanza CON-006 con un error por cada línea cuya cuenta no sirva. */
    private static void exigirCuentasImputables(List<LineaLeida> leidas) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (LineaLeida l : leidas) {
            ReglasCatalogo.comprobarImputable(l.cuenta(), "lineas[" + (l.posicion() - 1) + "].cuentaId")
                    .ifPresent(errores::add);
        }
        ReglasCatalogo.exigirImputables(errores);
    }

    /** Indica si la cuenta es el IVA débito o el IVA crédito de la configuración. */
    private static boolean esCuentaDeIva(Cuenta cuenta, ConfiguracionContable configuracion) {
        return cuenta.id().equals(configuracion.cuentaIvaDebito().id())
                || cuenta.id().equals(configuracion.cuentaIvaCredito().id());
    }

    /**
     * Agrega a la lista la línea expandida: tal cual, o dividida en base e IVA del mismo lado (CLAUDE.md 11.2).
     * El IVA va al Haber contra el IVA débito fiscal (venta) o al Debe contra el IVA crédito fiscal (compra).
     */
    private static void expandirLinea(
            LineaLeida l,
            ModoPrecio modo,
            BigDecimal tasa,
            ConfiguracionContable configuracion,
            List<LineaExpandida> destino) {
        boolean alDebe = !l.debe().esCero();
        // 1. Sin IVA: la línea pasa igual, con la posición que le toca en el asiento expandido
        if (!l.origen().llevaIva()) {
            destino.add(new LineaExpandida(
                    destino.size() + 1,
                    l.cuenta().resumen(),
                    l.origen().descripcion(),
                    l.debe(),
                    l.haber(),
                    OrigenLinea.USUARIO,
                    null));
            return;
        }

        // 2. Con IVA: la base queda en la misma cuenta y del mismo lado
        SeparacionIva separacion = CalculadoraIva.separar(l.monto(), modo, tasa);
        int posicionBase = destino.size() + 1;
        destino.add(new LineaExpandida(
                posicionBase,
                l.cuenta().resumen(),
                l.origen().descripcion(),
                alDebe ? separacion.base() : Dinero.CERO,
                alDebe ? Dinero.CERO : separacion.base(),
                OrigenLinea.USUARIO,
                null));

        // 3. Si el IVA redondea a cero (montos de centavos) no hay línea de IVA: una línea en cero viola el CHECK de un
        // solo lado
        if (separacion.iva().esCero()) {
            return;
        }
        var cuentaIva = alDebe ? configuracion.cuentaIvaCredito() : configuracion.cuentaIvaDebito();
        destino.add(new LineaExpandida(
                posicionBase + 1,
                cuentaIva,
                null,
                alDebe ? separacion.iva() : Dinero.CERO,
                alDebe ? Dinero.CERO : separacion.iva(),
                OrigenLinea.IVA_CALCULADO,
                posicionBase));
    }
}

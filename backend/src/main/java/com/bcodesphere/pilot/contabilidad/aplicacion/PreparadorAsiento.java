package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.AsientoExpandido;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ExpansorAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaSolicitud;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.SolicitudAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.iva.ConsultaTasaIva;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte común de la vista previa y del registro (CLAUDE.md 10.1): hace las lecturas (configuración, cuentas, tasa,
 * fecha de hoy) y entrega el asiento expandido con las reglas del dominio. No escribe nada; exige la transacción del
 * caso de uso.
 */
@Component
class PreparadorAsiento {

    private final RepositorioConfiguracionContable configuracion;
    private final RepositorioCuentas cuentas;
    private final ConsultaTasaIva tasas;
    private final Clock reloj;

    /**
     * Crea el preparador.
     *
     * @param configuracion puerto de la configuración contable
     * @param cuentas puerto del catálogo
     * @param tasas puerto de la tasa de IVA vigente
     * @param reloj reloj con la zona {@code America/El_Salvador}, para «hoy» (CON-007)
     */
    PreparadorAsiento(
            RepositorioConfiguracionContable configuracion,
            RepositorioCuentas cuentas,
            ConsultaTasaIva tasas,
            Clock reloj) {
        this.configuracion = configuracion;
        this.cuentas = cuentas;
        this.tasas = tasas;
        this.reloj = reloj;
    }

    /**
     * Hoy en hora de El Salvador.
     *
     * @return la fecha contable de hoy
     */
    LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /**
     * Lee lo necesario y expande el asiento.
     *
     * @param solicitud datos capturados
     * @return el asiento expandido; la partida doble aún no está validada
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si la empresa no tiene configuración contable
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    AsientoExpandido preparar(SolicitudAsiento solicitud) {
        // 1. Configuración de la empresa: modo de precio por defecto y cuentas de IVA
        ConfiguracionContable config = configuracion.obtener().orElseThrow(ExcepcionPlataforma::noEncontrado);

        // 2. Cada cuenta distinta se consulta una vez; las de otra empresa no aparecen (RLS y filtro por empresa)
        Map<UUID, Cuenta> porId = new HashMap<>();
        for (LineaSolicitud linea : solicitud.lineas()) {
            if (linea.cuentaId() != null && !porId.containsKey(linea.cuentaId())) {
                cuentas.buscar(linea.cuentaId()).ifPresent(c -> porId.put(c.id(), c));
            }
        }

        // 3. Las reglas de expansión y validación viven en el dominio
        return ExpansorAsiento.expandir(solicitud, hoy(), porId, config, tasas);
    }
}

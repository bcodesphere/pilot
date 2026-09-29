package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.plataforma.AplicacionInstalada;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Precarga de Contabilidad al instalarla en una empresa (ADR-030, ADR-035). Oyente síncrono de
 * {@link AplicacionInstalada}: corre dentro de la transacción de la instalación, así que si algo falla la excepción
 * sube al publicador y se revierte la instalación completa (criterio de F2). Por eso usa {@code @EventListener} y no
 * {@code @ApplicationModuleListener} ni {@code @TransactionalEventListener(AFTER_COMMIT)}, que son asíncronos o
 * posteriores al commit.
 */
@Component
public class PrecargarContabilidad {

    /** Código de la app en el catálogo de apps. */
    static final String CODIGO_APP = "contabilidad";

    private static final Logger LOG = LoggerFactory.getLogger(PrecargarContabilidad.class);

    private final PrecargaContable precarga;
    private final RegistroAuditoria auditoria;

    /**
     * Crea el oyente.
     *
     * @param precarga puerto que copia las plantillas a la empresa
     * @param auditoria puerto de auditoría
     */
    public PrecargarContabilidad(PrecargaContable precarga, RegistroAuditoria auditoria) {
        this.precarga = precarga;
        this.auditoria = auditoria;
    }

    /**
     * Copia el catálogo, la configuración y las reglas cuando la app instalada es Contabilidad.
     *
     * @param evento app instalada y empresa que la instaló
     */
    @EventListener
    public void alInstalar(AplicacionInstalada evento) {
        // 1. Solo actúa para su app: otras apps también publican este evento
        if (!CODIGO_APP.equals(evento.codigo())) {
            return;
        }

        // 2. Copia las plantillas; cualquier error sube y revierte la instalación entera
        PrecargaContable.Resumen resumen = precarga.precargar();

        // 3. Una sola fila de auditoría con los totales (entidad contabilidad, acción PRECARGAR)
        auditoria.registrar(
                "contabilidad",
                evento.empresaId().valor().toString(),
                "PRECARGAR",
                null,
                Map.of(
                        "cuentas", resumen.cuentas(),
                        "cuentasDetalle", resumen.cuentasDetalle(),
                        "reglas", resumen.reglas()));
        LOG.info(
                "Contabilidad precargada: {} cuentas ({} de detalle) y {} reglas",
                resumen.cuentas(),
                resumen.cuentasDetalle(),
                resumen.reglas());
    }
}
